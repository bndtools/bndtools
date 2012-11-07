package org.bndtools.builder.indexer;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Timer;
import java.util.TimerTask;

import org.bndtools.api.ILogger;
import org.bndtools.api.IStartupParticipant;
import org.bndtools.api.Logger;

import aQute.bnd.build.Project;
import bndtools.central.Central;

/**
 * BndWorkspaceProjectIndexer generates index files for projects in the BndWorkspace
 * that are missing them.
 * 
 * It rechecks periodically to ensure .index files are up to date across projects
 */
public class BndWorkspaceProjectIndexer implements IStartupParticipant {
    private static final int RECHECK_AFTER_MILLISECONDS = 5 * 60 * 1000; // 5 minutes
    private final ILogger logger = Logger.getLogger(BndWorkspaceProjectIndexer.class);
    private BuiltBundleIndexer indexer = new BuiltBundleIndexer();
    private Timer indexThread = null;
    
    private class IndexThreadTask extends TimerTask {
        @Override
        public void run() {
            if (indexThread != null) {
                indexThread.cancel();
            }
            try {
                for (Project model : Central.getWorkspace().getAllProjects()) {
                    if (model == null) continue;
                    
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
            }
            indexThread = new Timer();
            indexThread.schedule(new IndexThreadTask(), RECHECK_AFTER_MILLISECONDS);
        }
    }
    
    @Override
    public void start() {
        // Wait until the workspace is setup before starting to access projects
        Central.onWorkspaceInit(new Runnable() {
            @Override
            public void run() {
                new IndexThreadTask().run();
            }
        });
    }

    @Override
    public void stop() {
    }
}
