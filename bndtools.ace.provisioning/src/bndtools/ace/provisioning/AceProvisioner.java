package bndtools.ace.provisioning;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.amdatu.ace.client.AceClient;
import org.amdatu.ace.client.AceClientException;
import org.amdatu.ace.client.AceClientWorkspace;
import org.amdatu.ace.client.model.Artifact;
import org.amdatu.ace.client.model.Artifact2Feature;
import org.amdatu.ace.client.model.Artifact2FeatureBuilder;
import org.amdatu.ace.client.model.ArtifactBuilder;
import org.amdatu.ace.client.model.Distribution;
import org.amdatu.ace.client.model.Distribution2Target;
import org.amdatu.ace.client.model.Distribution2TargetBuilder;
import org.amdatu.ace.client.model.DistributionBuilder;
import org.amdatu.ace.client.model.Feature;
import org.amdatu.ace.client.model.Feature2Distribution;
import org.amdatu.ace.client.model.Feature2DistributionBuilder;
import org.amdatu.ace.client.model.FeatureBuilder;
import org.amdatu.ace.client.model.Target;
import org.amdatu.ace.client.model.TargetBuilder;

public class AceProvisioner {
	private AceClientWorkspace m_workspace;
	private String m_aceUrl = "http://localhost:8080";
	private String m_target = "default";
	private String m_feature = "default";
	private String m_distribution = "default";
	private Artifact[] m_artifacts;
	private String[] m_resourceIds;
	private String[] m_jarUrls;
	private int m_port;
	
	public static void main(String[] args) throws Exception {
		if(args.length != 6) {
			throw new IllegalArgumentException("Invalid arguments. Need: [eclipseport] [host] [feature] [distribution] [target] [bundl1;bundle2;bundle3...]");
		}
				
		int port = Integer.parseInt(args[0]);
		String host = args[1];
		String feature = args[2];
		String distribution = args[3];
		String target = args[4];
		String bundles = args[5];
		
		String[] jarUrls = bundles.split(";");
		
		new AceProvisioner(host, feature, distribution, target, jarUrls, port).installBundles();
	}

	public AceProvisioner(String host, String feature, String distribution, String target, String[] jarUrls, int port) {
		m_aceUrl = host;
		m_feature = feature;
		m_distribution = distribution;
		m_target = target;
		m_jarUrls = jarUrls;
		m_port = port;
		connectToServer();
	}

	private void installBundles() throws Exception {
		AceClient aceClient = new AceClient(m_aceUrl + "/client/work");
		m_workspace = aceClient.createNewWorkspace();

		createDefaultDistribution();
		createDefaultFeature();

		m_artifacts = m_workspace.getResources(Artifact.class);
		m_resourceIds = m_workspace.getResourceIds(Artifact.class);

		processBundles();

		m_workspace.commit();
	}

	private void connectToServer() {
		ExecutorService threadPool = Executors.newFixedThreadPool(1);
		threadPool.execute(new Runnable() {
			@Override
			public void run() {

				try {
					System.out.println("Listening on port " + m_port);
					Socket socket = new Socket("localhost", m_port);
					BufferedReader in = new BufferedReader(
							new InputStreamReader(socket.getInputStream()));
					String input;

					while ((input = in.readLine()) != null) {
						switch (SocketProtocol.valueOf(input)) {
						case UPDATE:
							installBundles();
							break;

						default:
							System.out.println("Unknown command: " + input);
							break;
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	private void processBundles() throws Exception, IOException,
			FileNotFoundException, AceClientException {
		for (String url : m_jarUrls) {
			Bundle bundle = parseManifest(new File(url));
			processJar(bundle);
		}
	}

	private Bundle parseManifest(File jar) {

		Manifest bundleManifest = readManifestFromJar(jar);

		Bundle jarBundle = new Bundle(bundleManifest.getMainAttributes()
				.getValue("Bundle-SymbolicName"), bundleManifest
				.getMainAttributes().getValue("Bundle-Name"), bundleManifest
				.getMainAttributes().getValue("Bundle-Version"), jar.getPath());

		return jarBundle;
	}

	private Manifest readManifestFromJar(File jar) {
		JarInputStream jis = null;
		try {
			jis = new JarInputStream(new FileInputStream(jar));
			Manifest bundleManifest = jis.getManifest();
			if (bundleManifest == null) {
				System.out.println("Not a valid manifest in: " + jar);
			}

			return bundleManifest;
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				jis.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return null;
	}

	private void processJar(Bundle jar) throws IOException,
			FileNotFoundException, AceClientException {

		String url = jar.getUrl();
		int obrCacheIdx = url.indexOf(".obrcache/");
		if (obrCacheIdx != -1) {
			String part1 = url.substring(0, obrCacheIdx + 10);
			int lastIndexOfSlash = url.lastIndexOf("/");
			String part2 = URLEncoder.encode(
					url.substring(obrCacheIdx + 10, lastIndexOfSlash), "UTF-8");
			String part3 = url.substring(lastIndexOfSlash);
			url = part1 + part2 + part3;
		}
		ArtifactBuilder artifactBuilder = new ArtifactBuilder()
				.setUrl("file:" + url)
				.setMimeType("application/vnd.osgi.bundle")
				.setBundleSymbolicName(jar.getSymbolicName())
				.setBundleVersion(jar.getVersion()).setName(jar.getName());

		Artifact artifact = artifactBuilder.build();
		if (!artifactVersionExists(artifact)) {
			if (artifactBundleSymoblicNameExists(artifact)) {

				String resourceId = getResourceId(artifact);
				m_workspace.deleteResource(Artifact.class, resourceId);
			}

			m_workspace.createResource(artifact);
			System.out.println("Creating artifact "
					+ artifact.getBundleSymbolicName() + " - "
					+ artifact.getBundleVersion() + " - "
					+ artifact.getBundleVersion() + " - " + artifact.getUrl());

			createArtifact2Feature(artifact);
		}
	}

	private boolean artifactBundleSymoblicNameExists(Artifact artifact) {
		for (Artifact existingArtifact : m_artifacts) {

			if (existingArtifact.getBundleSymbolicName() != null
					&& existingArtifact.getBundleSymbolicName().equals(
							artifact.getBundleSymbolicName())) {
				return true;
			}
		}

		return false;
	}	

	private String getResourceId(Artifact artifact) {
		for (String id : m_resourceIds) {

			if (id.contains("Bundle-SymbolicName-"
					+ artifact.getBundleSymbolicName() + "-Bundle-Version-")) {
				return id;
			}
		}

		return null;
	}

	private boolean artifactVersionExists(Artifact artifact)
			throws AceClientException {
		Artifact[] artifacts = m_workspace.getResources(Artifact.class);
		for (Artifact repoArtifact : artifacts) {
			if (repoArtifact.getMimetype()
					.equals("application/vnd.osgi.bundle")
					&& repoArtifact.getBundleSymbolicName().equals(
							artifact.getBundleSymbolicName())
					&& repoArtifact.getBundleVersion().equals(
							artifact.getBundleVersion())) {

				return true;
			}
		}

		return false;
	}

	private void createArtifact2Feature(Artifact artifact)
			throws AceClientException {
		if (!artifact2FeatureExists(artifact)) {

			Artifact2Feature artifact2Feature = new Artifact2FeatureBuilder()
					.setLeftEndpoint(
							"(&(Bundle-SymbolicName="
									+ artifact.getBundleSymbolicName()
									+ ")(Bundle-Version="
									+ artifact.getBundleVersion() + "))")
					.setRightEndpoint("(name=" + m_feature + ")")
					.setAttribute("left", "*").setAttribute("right", "*")
					.build();

			m_workspace.createResource(artifact2Feature);
		}
	}

	private void createDefaultFeature() throws AceClientException {
		if (!defaultFeatureExisists()) {
			Feature feature = new FeatureBuilder().setName(m_feature).build();
			m_workspace.createResource(feature);
		}

		createFeature2Distribution();
	}

	private void createFeature2Distribution() throws AceClientException {
		if (!defaultFeature2DistributionExisists()) {
			Feature2Distribution feature2Distribution = new Feature2DistributionBuilder()
					.setLeftEndpoint("(name=" + m_feature + ")")
					.setRightEndpoint("(name=" + m_distribution + ")").build();
			m_workspace.createResource(feature2Distribution);
		}
	}

	private void createDefaultDistribution() throws AceClientException {
		if (!defaultDistributionExisists()) {
			Distribution dist = new DistributionBuilder().setName(
					m_distribution).build();
			m_workspace.createResource(dist);

			createTarget();
			createDistribution2Target();

		}
	}

	private void createTarget() throws AceClientException {
		Target target = new TargetBuilder().setId(m_target).build();
		try {
			m_workspace.createResource(target);
		} catch (AceClientException ex) {
			// ignore, this happens when target already exists...
		}
	}

	private void createDistribution2Target() throws AceClientException {
		if (!defaultDistribution2TargetExists()) {
			Distribution2Target distribution2Target = new Distribution2TargetBuilder()
					.setLeftEndpoint("(name=" + m_distribution + ")")
					.setRightEndpoint("(id=" + m_target + ")").build();
			m_workspace.createResource(distribution2Target);
		}
	}

	private boolean defaultFeatureExisists() throws AceClientException {
		Feature[] features = m_workspace.getResources(Feature.class);
		for (Feature f : features) {
			if (f.getName().equals(m_feature)) {
				return true;
			}
		}

		return false;
	}

	private boolean artifact2FeatureExists(Artifact artifact)
			throws AceClientException {
		Artifact2Feature[] links = m_workspace
				.getResources(Artifact2Feature.class);
		for (Artifact2Feature a2f : links) {

			if (a2f.getLeftEndpoint().equals(
					"(&(Bundle-SymbolicName="
							+ artifact.getBundleSymbolicName()
							+ ")(Bundle-Version=" + artifact.getBundleVersion()
							+ "))")
					&& a2f.getRightEndpoint()
							.equals("(name=" + m_feature + ")")) {
				return true;
			}
		}

		return false;
	}

	private boolean defaultFeature2DistributionExisists()
			throws AceClientException {
		Feature2Distribution[] features = m_workspace
				.getResources(Feature2Distribution.class);
		for (Feature2Distribution f : features) {
			if (f.getLeftEndpoint().equals("(name=" + m_feature + ")")
					&& f.getRightEndpoint().equals(
							"(name=" + m_distribution + ")")) {
				return true;
			}
		}

		return false;
	}

	private boolean defaultDistributionExisists() throws AceClientException {
		Distribution[] distributions = m_workspace
				.getResources(Distribution.class);
		for (Distribution d : distributions) {
			if (d.getName().equals(m_distribution)) {
				return true;
			}
		}

		return false;
	}

	private boolean defaultDistribution2TargetExists()
			throws AceClientException {
		Distribution2Target[] features = m_workspace
				.getResources(Distribution2Target.class);
		for (Distribution2Target f : features) {
			if (f.getLeftEndpoint().equals("(name=" + m_distribution + ")")
					&& f.getRightEndpoint().equals("(id=" + m_target + ")")) {
				return true;
			}
		}

		return false;
	}

	class Bundle {
		private final String symbolicName;
		private final String name;
		private final String version;
		private final String url;

		public Bundle(String symbolicName, String name, String version,
				String url) {
			this.symbolicName = symbolicName;
			this.name = name;
			this.version = version;
			this.url = url;
		}

		public String getSymbolicName() {
			return symbolicName;
		}

		public String getName() {
			return name;
		}

		public String getVersion() {
			return version;
		}

		public String getUrl() {
			return url;
		}

	}

}
