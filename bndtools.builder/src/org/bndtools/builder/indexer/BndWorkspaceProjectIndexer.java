package org.bndtools.builder.indexer;

import java.io.File;
import java.io.FilenameFilter;

import org.bndtools.api.ILogger;
import org.bndtools.api.IStartupParticipant;
import org.bndtools.api.Logger;
import org.eclipse.core.internal.jobs.JobStatus;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;

import aQute.bnd.build.Project;
import bndtools.central.Central;

/**
 * BndWorkspaceProjectIndexer generates index files for projects in the BndWorkspace
 * that are missing them.
 * 
 * It rechecks periodically to ensure .index files are up to date across projects
 */
public class BndWorkspaceProjectIndexer implements IStartupParticipant {
    private final ILogger logger = Logger.getLogger(BndWorkspaceProjectIndexer.class);
    private BuiltBundleIndexer indexer = new BuiltBundleIndexer();
    
    /**
     * BndWorkspaceProjectIndexerJob implements the eclipse WorkspaceJob
     * interface. When run, it refreshes index files of projects in the bnd workspace.
     */
    private class BndWorkspaceProjectIndexerJob extends WorkspaceJob {
        public BndWorkspaceProjectIndexerJob() {
            super("Refreshing closed project index files");
            setPriority(DECORATE);
            setSystem(true);
        }

        @Override
        public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
            IStatus ret = new JobStatus(JobStatus.OK, this, "Completed indexing.");
            try {
                for (Project model : Central.getWorkspace().getAllProjects()) {
                    if (model == null) continue;
                    
                    // If no eclipse relating project OR if eclipse project is closed, then
                    // index
                    //[cs] 1/10/2014 update -- turns out that if you have open projects and then build from command line, the .index is deleted, but then
                    // never recreated... Commenting out this code should make it so that all .index file will be regenerated eventually. 
//                    IProject p = Central.getProject(model);
//                    if (p != null && p.isOpen()) {
//                        continue;
//                    }
                    
                    File targetDir = model.getTarget();
                    if (targetDir != null) {
                        
                        File indexFile = new File(targetDir, ".index");
                        boolean needRegen=false;
                        if (indexFile.isFile()) {
                            
                            // Look for files that could indicate the index is out of date.
                            File[] files = targetDir.listFiles(new FilenameFilter() {
                                @Override
                                public boolean accept(File dir, String name) {
                                    return name.toLowerCase().endsWith(".jar");
                                }
                            });
                            
                            for(File f : files) {
                                if (f.lastModified() > indexFile.lastModified()) {
                                    needRegen = true;
                                    break;
                                }
                            }
                        }
                        if (!indexFile.isFile() || needRegen) {
                            
                            indexer.builtBundles(model, targetDir.listFiles(new FilenameFilter() {
                                @Override
                                public boolean accept(File dir, String name) {
                                    return name.endsWith(".jar");
                                }
                            }));
                        }
                    }
                }
            } catch (Exception e) {
                logger.logError("Unable to finish indexing...", e);
                ret = new JobStatus(JobStatus.ERROR, this, "Failure during indexing.");
            }
            scheduleRecheck();
            return ret;
        }
    }
    
    private void scheduleFirst() {
        new BndWorkspaceProjectIndexerJob().schedule(1 * 60 * 1000); // 1 minutes
    }

    private void scheduleRecheck() {
        new BndWorkspaceProjectIndexerJob().schedule(5 * 60 * 1000); // 5 minutes
    }
    
    @Override
    public void start() {
        // Wait until the workspace is setup before starting to access projects
        Central.onWorkspaceInit(new Runnable() {
            @Override
            public void run() {
                scheduleFirst();
            }
        });
    }

    @Override
    public void stop() {
    }
}
