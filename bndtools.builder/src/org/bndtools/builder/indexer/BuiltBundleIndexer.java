package org.bndtools.builder.indexer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bndtools.api.ILogger;
import org.bndtools.api.Logger;
import org.bndtools.build.api.AbstractBuildListener;
import org.bndtools.utils.log.LogServiceAdapter;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.osgi.service.indexer.Builder;
import org.osgi.service.indexer.Capability;
import org.osgi.service.indexer.Requirement;
import org.osgi.service.indexer.Resource;
import org.osgi.service.indexer.ResourceAnalyzer;
import org.osgi.service.indexer.ResourceIndexer;
import org.osgi.service.indexer.impl.RepoIndex;

import aQute.bnd.build.Project;
import aQute.lib.io.IO;
import bndtools.central.BndWorkspaceRepository;
import bndtools.central.Central;
import bndtools.central.WorkspaceR5Repository;

public class BuiltBundleIndexer extends AbstractBuildListener {
    private final ILogger logger = Logger.getLogger(BuiltBundleIndexer.class);
    private final LogServiceAdapter logAdapter;
    private static final String INDEX_FILENAME = ".index";
    
    public BuiltBundleIndexer() {
        logAdapter = new LogServiceAdapter(logger);
    }
    
    private Set<File> pathsToFiles(IPath[] paths) {
        IWorkspaceRoot wsroot = ResourcesPlugin.getWorkspace().getRoot();

        Set<File> files = new HashSet<File>();
        for (IPath path : paths) {
            try {
                IFile ifile = wsroot.getFile(path);
                IPath location = ifile.getLocation();
                if (location != null)
                    files.add(location.toFile());
            } catch (IllegalArgumentException e) {
                logger.logError("### Error processing path: " + path, e);
                e.printStackTrace();
            }
        }
        return files;
    }
    
    @Override
    public void builtBundles(final IProject project, IPath[] paths) {
        logger.logInfo("BuiltBundleIndexer.builtBundles with IProject:" + project.getName(), null);
        Set<File> files = pathsToFiles(paths);

        Project model;
        try {
            model = Central.getProject(project.getLocation().toFile());
        } catch (Exception e) {
            logger.logError(MessageFormat.format("Failed to generate index file for bundles in project {0}.", project.getName()), e);
            return;
        }
        File indexFile = builtBundles(files, model, project.getName(), project.getLocation().toFile().toURI(), project.getFullPath().toString());
        addToWorkspaceRepositories(project, indexFile);
        addToBndWorkspaceRepositories(model, indexFile);
    }

    /**
     * Indexes project.
     * 
     * @param model Project
     * @param paths Does nothing if null or empty.
     * @return IndexFile
     */
    public void builtBundles(final Project model, File[] files) {
        logger.logInfo("BuiltBundleIndexer.builtBundles with Project:" + model.getName(), null);
        if (files == null || files.length == 0) {
            return;
        }
        HashSet<File> files2 = new HashSet<File>(Arrays.asList(files));
        File indexFile = builtBundles(files2, model, model.getName(), model.getBase().toURI(), model.getBase().getAbsolutePath());
        addToWorkspaceRepositories(Central.getProject(model), indexFile);
        addToBndWorkspaceRepositories(model, indexFile);
    }

    private File builtBundles(Set<File> files, final Project model, String projectName, URI projectUrl, final String fullPath) {
        IWorkspaceRoot wsroot = ResourcesPlugin.getWorkspace().getRoot();
        final URI workspaceRootUri = wsroot.getLocationURI();

        // Generate the index file
        File indexFile;
        OutputStream output = null;
        try {
            File target = model.getTarget();
            indexFile = new File(target, INDEX_FILENAME);

            IFile indexPath = wsroot.getFile(Central.toPath(indexFile));

            // Create the indexer and add ResourceAnalyzers from plugins
            RepoIndex indexer = new RepoIndex(logAdapter);
            List<ResourceAnalyzer> analyzers = Central.getWorkspace().getPlugins(ResourceAnalyzer.class);
            for (ResourceAnalyzer analyzer : analyzers) {
                indexer.addAnalyzer(analyzer, null);
            }

            // Use an analyzer to add a marker capability to workspace resources
            indexer.addAnalyzer(new ResourceAnalyzer() {
                public void analyzeResource(Resource resource, List<Capability> capabilities, List<Requirement> requirements) throws Exception {
                    Capability cap = new Builder().setNamespace("bndtools.workspace").addAttribute("bndtools.workspace", workspaceRootUri.toString()).addAttribute("project.path", fullPath).buildCapability();
                    capabilities.add(cap);
                }
            }, null);

            Map<String,String> config = new HashMap<String,String>();
            config.put(ResourceIndexer.REPOSITORY_NAME, projectName);
            config.put(ResourceIndexer.ROOT_URL, projectUrl.toString());
            config.put(ResourceIndexer.PRETTY, "true");

            output = new FileOutputStream(indexFile);
            indexer.index(files, output, config);
            IO.close(output);
            indexPath.refreshLocal(IResource.DEPTH_ZERO, null);
            if (indexPath.exists())
                indexPath.setDerived(true, null);
        } catch (Exception e) {
            logger.logError(MessageFormat.format("Failed to generate index file for bundles in project {0}.", projectName), e);
            return null;
        } finally {
            IO.close(output);
        }
        return indexFile;
    }
    
    private void addToWorkspaceRepositories(IProject project, File indexFile) {
        if (project == null || indexFile == null) {
            logger.logInfo("BuiltBundleIndexer.addToWorkspaceRepositories. project=" + project + " indexFile=" + indexFile, null);
            return;
        }
        logger.logInfo("BuiltBundleIndexer.addToWorkspaceRepositories:" + project.getName(), null);

        // Parse the index and add to the workspace repository
        FileInputStream input = null;
        try {
            input = new FileInputStream(indexFile);
            WorkspaceR5Repository workspaceRepo = Central.getWorkspaceR5Repository();
            if (workspaceRepo != null && project.getLocation() != null && project.getLocation().toFile() != null && project.getLocation().toFile().toURI() != null) {
                workspaceRepo.loadProjectIndex(project, input, project.getLocation().toFile().toURI());
            } else {
                logger.logError("Unable to load Project index into workspaceRepo for project=" + project.getName() + " workspaceRepo=" + workspaceRepo, null);
            }

            // Need a new FileInputStream because loadProjectIndex above closes stream.
            input = new FileInputStream(indexFile);
            Project p = Central.getInstance().getModel(project);
            BndWorkspaceRepository bndWorkspaceRepo = Central.getBndWorkspaceRepository();
            bndWorkspaceRepo.loadProjectIndex(p, input);
        } catch (Exception e) {
            logger.logError("Failed to update workspace index.", e);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    /* ignore */
                }
            }
        }
    }
    
    private void addToBndWorkspaceRepositories(Project project, File indexFile) {
        if (project == null || indexFile == null) {
            logger.logError("BuiltBundleIndexer.addToBndWorkspaceRepositories. project=" + project + " indexFile=" + indexFile, null);
            return;
        }
        logger.logInfo("BuiltBundleIndexer.addToBndWorkspaceRepositories:" + project.getName(), null);

        // Parse the index and add to the workspace repository
        FileInputStream input = null;

        try {
            // Need a new FileInputStream because loadProjectIndex above closes stream.
            input = new FileInputStream(indexFile);
            BndWorkspaceRepository bndWorkspaceRepo = Central.getBndWorkspaceRepository();
            bndWorkspaceRepo.loadProjectIndex(project, input);
        } catch (Exception e) {
            logger.logError("Failed to update workspace index.", e);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    /* ignore */
                }
            }
        }
    }


}
