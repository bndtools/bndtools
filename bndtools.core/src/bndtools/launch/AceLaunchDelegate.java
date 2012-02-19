package bndtools.launch;

import java.io.FileNotFoundException;
import java.io.IOException;

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
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.LaunchConfigurationDelegate;

import aQute.bnd.build.Container;
import aQute.bnd.build.Project;

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
    }

    private void installBundles() throws Exception {
        AceClient aceClient = new AceClient(m_aceUrl + "/client/work");
        workspace = aceClient.createNewWorkspace();

        createDefaultDistribution();
        createDefaultFeature();

        artifacts = workspace.getResources(Artifact.class);
        resourceIds = workspace.getResourceIds(Artifact.class);

        for (Container bundle : project.getRunbundles()) {
             processJar(bundle);
        }

        workspace.commit();
    }

    private void processJar(Container jar) throws IOException, FileNotFoundException, AceClientException {
        Artifact artifact = new ArtifactBuilder().setUrl(jar.getFile().getAbsolutePath()).setMimeType("application/vnd.osgi.bundle")
                .setBundleSymbolicName(jar.getBundleSymbolicName()).setBundleVersion(jar.getVersion()).setName(jar.getBundleSymbolicName()).build();

        if (!artifactUrlExists(artifact) && !artifactVersionExists(artifact)) {
            if (artifactBundleSymoblicNameExists(artifact)) {

                workspace.deleteResource(Artifact.class, getResourceId(artifact));
            }

            //TODO: uploadArtifact(artifact);
            workspace.createResource(artifact);

            createArtifact2Feature(artifact);
        }
    }
    
    private boolean artifactBundleSymoblicNameExists(Artifact artifact) {
        for (Artifact existingArtifact : artifacts) {

            if (existingArtifact.getBundleSymbolicName() != null && existingArtifact.getBundleSymbolicName().equals(
                    artifact.getBundleSymbolicName() )) {
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

            if (id.contains("Bundle-SymbolicName-"
                    + artifact.getBundleSymbolicName() + "-Bundle-Version-")) {
                System.out.println(id);
                return id;
            }
        }

        return null;
    }
    
    private boolean artifactVersionExists(Artifact artifact)
            throws AceClientException {
        Artifact[] artifacts = workspace.getResources(Artifact.class);
        for (Artifact repoArtifact : artifacts) {
            if (repoArtifact.getMimetype().equals("application/vnd.osgi.bundle") && 
                    repoArtifact.getBundleSymbolicName().equals(
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
        Artifact2Feature artifact2Feature = new Artifact2FeatureBuilder()
                .setLeftEndpoint(
                        "(&(Bundle-SymbolicName="
                                + artifact.getBundleSymbolicName()
                                + ")(Bundle-Version="
                                + artifact.getBundleVersion() + "))")
                .setRightEndpoint("(name=" + m_feature + ")")
                .setAttribute("left", "*").setAttribute("right", "*").build();

        workspace.createResource(artifact2Feature);
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
}
