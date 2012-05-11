package bndtools.launch;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.launching.JavaLaunchDelegate;

import aQute.bnd.build.Container;
import aQute.bnd.build.Project;
import bndtools.Central;
import bndtools.Plugin;

public class AceLaunchDelegate extends JavaLaunchDelegate {
    public final static String CONSOLE_NAME = "ACE";
    private Project m_project;
    private AceLaunchSocket m_aceLaunchSocket;
    private int m_port;
    private String m_aceUrl;
    private String m_feature;
    private String m_distribution;
    private String m_target;

    public AceLaunchDelegate() {
        m_aceLaunchSocket = new AceLaunchSocket();
        m_port = m_aceLaunchSocket.openSocket();
    }

    @Override
    public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor) throws CoreException {
        m_aceLaunchSocket.start();
        m_aceUrl = configuration.getAttribute(LaunchConstants.ATTR_ACE_ADDRESS, "http://localhost:8080");
        m_feature = configuration.getAttribute(LaunchConstants.ATTR_ACE_FEATURE, "default");
        m_distribution = configuration.getAttribute(LaunchConstants.ATTR_ACE_DISTRIBUTION, "default");
        m_target = configuration.getAttribute(LaunchConstants.ATTR_ACE_TARGET, "default");
        
        super.launch(configuration, mode, launch, monitor);
        registerLaunchPropertiesRegenerator(m_project, launch);
    }

    @Override
    public String getMainTypeName(ILaunchConfiguration configuration) throws CoreException {
        return "bndtools.ace.provisioning.AceProvisioner";
    }

    @Override
    public String[] getClasspath(ILaunchConfiguration configuration) throws CoreException {
        org.osgi.framework.Bundle bundle = Platform.getBundle("bndtools.ace.provisioning");

        String location = bundle.getLocation();
        return new String[] { location };
    }

    @Override
    public String getProgramArguments(ILaunchConfiguration configuration) {
        try {
            m_project = LaunchUtils.getBndProject(configuration);
            
            StringBuilder sb = new StringBuilder();
            sb.append(m_port).append(" ")
               .append(m_aceUrl).append(" ")
               .append(m_feature).append(" ")
               .append(m_distribution).append(" ")
               .append(m_target).append(" ");
            
            for (Container bundle : m_project.getDeliverables()) {
                sb.append(bundle.getFile().getAbsolutePath()).append(";");
            }
            
            for (Container bundle : m_project.getRunbundles()) {
                sb.append(bundle.getFile().getAbsolutePath()).append(";");
            }
            
            
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

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
                        final Set<String> runBundleSet = new HashSet<String>();
                        for (Container bundle : m_project.getRunbundles()) {
                            runBundleSet.add(bundle.getFile().getAbsolutePath());
                        }

                        event.getDelta().accept(new IResourceDeltaVisitor() {
                            public boolean visit(IResourceDelta delta) throws CoreException {
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
                        System.out.println("updated");
                        project.forceRefresh();
                        project.setChanged();
                        m_aceLaunchSocket.sendUpdated();
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

}
