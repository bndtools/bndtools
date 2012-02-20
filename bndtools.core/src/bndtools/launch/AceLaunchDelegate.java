package bndtools.launch;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.LaunchConfigurationDelegate;

import aQute.bnd.build.Container;
import aQute.bnd.build.Project;
import bndtools.Central;
import bndtools.Plugin;

public class AceLaunchDelegate extends LaunchConfigurationDelegate {
    private Project project;
    private AceClientWorkspace workspace;
    private String m_aceUrl = "http://localhost:8080";
    private String m_target = "default";
    private String m_feature = "default";
    private String m_distribution = "default";
    private Artifact[] artifacts;
    private String[] resourceIds;

    public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor) throws CoreException {
        project = LaunchUtils.getBndProject(configuration);

        try {
            installBundles();
        } catch (Exception e) {
            e.printStackTrace();
        }

        registerLaunchPropertiesRegenerator(project, launch);
    }

    private void installBundles() throws Exception {
        AceClient aceClient = new AceClient(m_aceUrl + "/client/work");
        workspace = aceClient.createNewWorkspace();

        createDefaultDistribution();
        createDefaultFeature();

        artifacts = workspace.getResources(Artifact.class);
        resourceIds = workspace.getResourceIds(Artifact.class);

        processBundles();

        workspace.commit();
    }

    private void processBundles() throws Exception, IOException, FileNotFoundException, AceClientException {

        for (Container bundle : project.getRunbundles()) {
            processJar(bundle);

        }

    }

    private Bundle parseManifest(File jar) {
        Bundle jarBundle = new Bundle();

        Manifest bundleManifest = readManifestFromJar(jar);

        jarBundle.name = bundleManifest.getMainAttributes().getValue("Bundle-SymbolicName");
        jarBundle.version = bundleManifest.getMainAttributes().getValue("Bundle-Version");

        return jarBundle;
    }

    private Manifest readManifestFromJar(File jar) {
        JarInputStream jis = null;
        try {
            jis = new JarInputStream(new FileInputStream(jar));
            Manifest bundleManifest = jis.getManifest();
            if (bundleManifest == null) {
                System.err.println("Not a valid manifest in: " + jar);
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

    private void processJar(Container jar) throws IOException, FileNotFoundException, AceClientException {
        System.out.println("Processing bundle: " + jar);
        System.out.println("Name: " + jar.getBundleSymbolicName().replace(".jar", ""));
        System.out.println("Version: " + jar.getVersion());
        ArtifactBuilder artifactBuilder = new ArtifactBuilder().setUrl("file://" + jar.getFile().getAbsolutePath()).setMimeType("application/vnd.osgi.bundle")
                .setBundleSymbolicName(jar.getBundleSymbolicName()).setBundleVersion(jar.getVersion())
                .setName(jar.getBundleSymbolicName().replace(".jar", ""));

        boolean local = false;

        // TODO: this is a work around, why doesn't the jar get a version?
        if (jar.getVersion().equals("project")) {
            Bundle manifest = parseManifest(jar.getFile());
            artifactBuilder.setBundleVersion(manifest.version);
            artifactBuilder.setBundleSymbolicName(manifest.name);
            artifactBuilder.setName(manifest.name);
            local = true;
        }

        Artifact artifact = artifactBuilder.build();

        System.out.println(artifact.getBundleVersion());

        if (local || (!artifactUrlExists(artifact) && !artifactVersionExists(artifact))) {
            if (artifactBundleSymoblicNameExists(artifact)) {

                workspace.deleteResource(Artifact.class, getResourceId(artifact));
                System.out.println("deleting");
            }

            // TODO: uploadArtifact(artifact);
            workspace.createResource(artifact);

            createArtifact2Feature(artifact);
        }
    }

    private boolean artifactBundleSymoblicNameExists(Artifact artifact) {
        for (Artifact existingArtifact : artifacts) {

            if (existingArtifact.getBundleSymbolicName() != null && existingArtifact.getBundleSymbolicName().equals(artifact.getBundleSymbolicName())) {
                return true;
            }
        }

        return false;
    }

    private boolean artifactUrlExists(Artifact artifact) {
        for (Artifact existingArtifact : artifacts) {
            if (existingArtifact.getUrl().equals(artifact.getUrl())) {
                return true;
            }
        }

        return false;
    }

    private String getResourceId(Artifact artifact) {
        for (String id : resourceIds) {

            if (id.contains("Bundle-SymbolicName-" + artifact.getBundleSymbolicName() + "-Bundle-Version-")) {
                System.out.println(id);
                return id;
            }
        }

        return null;
    }

    private boolean artifactVersionExists(Artifact artifact) throws AceClientException {
        Artifact[] artifacts = workspace.getResources(Artifact.class);
        for (Artifact repoArtifact : artifacts) {
            if (repoArtifact.getMimetype().equals("application/vnd.osgi.bundle")
                    && repoArtifact.getBundleSymbolicName().equals(artifact.getBundleSymbolicName())
                    && repoArtifact.getBundleVersion().equals(artifact.getBundleVersion())) {

                return true;
            }
        }

        return false;
    }

    private void createArtifact2Feature(Artifact artifact) throws AceClientException {
        Artifact2Feature artifact2Feature = new Artifact2FeatureBuilder()
                .setLeftEndpoint("(&(Bundle-SymbolicName=" + artifact.getBundleSymbolicName() + ")(Bundle-Version=" + artifact.getBundleVersion() + "))")
                .setRightEndpoint("(name=" + m_feature + ")").setAttribute("left", "*").setAttribute("right", "*").build();

        try {
            workspace.createResource(artifact2Feature);
        } catch (Exception ex) {
            System.out.println("Failed to create artifact2feature");
        }

    }

    private void createDefaultFeature() throws AceClientException {
        if (!defaultFeatureExisists()) {
            Feature feature = new FeatureBuilder().setName(m_feature).build();
            workspace.createResource(feature);
        }

        createFeature2Distribution();
    }

    private void createFeature2Distribution() throws AceClientException {
        if (!defaultFeature2DistributionExisists()) {
            Feature2Distribution feature2Distribution = new Feature2DistributionBuilder().setLeftEndpoint("(name=" + m_feature + ")")
                    .setRightEndpoint("(name=" + m_distribution + ")").build();
            workspace.createResource(feature2Distribution);
        }
    }

    private void createDefaultDistribution() throws AceClientException {
        if (!defaultDistributionExisists()) {
            Distribution dist = new DistributionBuilder().setName(m_distribution).build();
            workspace.createResource(dist);

            createTarget();
            createDistribution2Target();

        }
    }

    private void createTarget() throws AceClientException {
        Target target = new TargetBuilder().setId(m_target).build();
        try {
            workspace.createResource(target);
        } catch (AceClientException ex) {
            // ignore, this happens when target already exists...
        }
    }

    private void createDistribution2Target() throws AceClientException {
        if (!defaultDistribution2TargetExists()) {
            Distribution2Target distribution2Target = new Distribution2TargetBuilder().setLeftEndpoint("(name=" + m_distribution + ")")
                    .setRightEndpoint("(id=" + m_target + ")").build();
            workspace.createResource(distribution2Target);
        }
    }

    private boolean defaultFeatureExisists() throws AceClientException {
        Feature[] features = workspace.getResources(Feature.class);
        for (Feature f : features) {
            if (f.getName().equals(m_feature)) {
                return true;
            }
        }

        return false;
    }

    private boolean defaultFeature2DistributionExisists() throws AceClientException {
        Feature2Distribution[] features = workspace.getResources(Feature2Distribution.class);
        for (Feature2Distribution f : features) {
            if (f.getLeftEndpoint().equals("(name=" + m_feature + ")") && f.getRightEndpoint().equals("(name=" + m_distribution + ")")) {
                return true;
            }
        }

        return false;
    }

    private boolean defaultDistributionExisists() throws AceClientException {
        Distribution[] distributions = workspace.getResources(Distribution.class);
        for (Distribution d : distributions) {
            if (d.getName().equals(m_distribution)) {
                return true;
            }
        }

        return false;
    }

    private boolean defaultDistribution2TargetExists() throws AceClientException {
        Distribution2Target[] features = workspace.getResources(Distribution2Target.class);
        for (Distribution2Target f : features) {
            if (f.getLeftEndpoint().equals("(name=" + m_distribution + ")") && f.getRightEndpoint().equals("(id=" + m_target + ")")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Registers a resource listener with the project model file to update the
     * launcher when the model or any of the run-bundles changes. The resource
     * listener is automatically unregistered when the launched process
     * terminates.
     * 
     * @param project
     * @param launch
     * @throws CoreException
     */
    private void registerLaunchPropertiesRegenerator(final Project project, final ILaunch launch) throws CoreException {
        final IResource targetResource = LaunchUtils.getTargetResource(launch.getLaunchConfiguration());
        final IPath bndbndPath;
        try {
            bndbndPath = Central.toPath(project.getPropertiesFile());
        } catch (Exception e) {
            throw new CoreException(new Status(IStatus.ERROR, Plugin.PLUGIN_ID, 0, "Error querying bnd.bnd file location", e));
        }

        final IPath targetPath;
        try {
            targetPath = Central.toPath(project.getTarget());
        } catch (Exception e) {
            throw new CoreException(new Status(IStatus.ERROR, Plugin.PLUGIN_ID, 0, "Error querying project output folder", e));
        }
        final IResourceChangeListener resourceListener = new IResourceChangeListener() {
            public void resourceChanged(IResourceChangeEvent event) {
                try {
                    final AtomicBoolean update = new AtomicBoolean(false);

                    // Was the properties file (bnd.bnd or *.bndrun) included in
                    // the delta?
                    IResourceDelta propsDelta = event.getDelta().findMember(bndbndPath);
                    if (propsDelta == null && targetResource.getType() == IResource.FILE)
                        propsDelta = event.getDelta().findMember(targetResource.getFullPath());
                    if (propsDelta != null) {
                        if (propsDelta.getKind() == IResourceDelta.CHANGED) {
                            update.set(true);
                        }
                    }

                    // Check for bundles included in the launcher's runbundles
                    // list
                    if (!update.get()) {
                        final Collection<Container> runBundleSet = project.getRunbundles();
                        event.getDelta().accept(new IResourceDeltaVisitor() {
                            public boolean visit(IResourceDelta delta) throws CoreException {
                                // Short circuit if we have already found a
                                // match
                                if (update.get())
                                    return false;

                                IResource resource = delta.getResource();
                                if (resource.getType() == IResource.FILE) {
                                    boolean isRunBundle = runBundleSet.contains(resource.getLocation().toPortableString());
                                    update.compareAndSet(false, isRunBundle);
                                    return false;
                                }

                                // Recurse into containers
                                return true;
                            }
                        });
                    }

                    // Was the target path included in the delta? This might
                    // mean that sub-bundles have changed
                    boolean targetPathChanged = event.getDelta().findMember(targetPath) != null;
                    update.compareAndSet(false, targetPathChanged);

                    if (update.get()) {
                        project.forceRefresh();
                        project.setChanged();
                        processBundles();
                        System.out.println("updating!");
                    }
                } catch (Exception e) {
                    IStatus status = new Status(IStatus.ERROR, Plugin.PLUGIN_ID, 0, "Error updating launch properties file.", e);
                    Plugin.log(status);
                }
            }
        };
        ResourcesPlugin.getWorkspace().addResourceChangeListener(resourceListener);

        // Register a listener for termination of the launched process
        Runnable onTerminate = new Runnable() {
            public void run() {
                ResourcesPlugin.getWorkspace().removeResourceChangeListener(resourceListener);
            }
        };
        DebugPlugin.getDefault().addDebugEventListener(new TerminationListener(launch, onTerminate));
    }

    class Bundle {
        public String name;
        public String version;
    }
}
