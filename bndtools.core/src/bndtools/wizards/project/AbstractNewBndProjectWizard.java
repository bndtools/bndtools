/*******************************************************************************
 * Copyright (c) 2010 Neil Bartlett.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Neil Bartlett - initial API and implementation
 *******************************************************************************/
package bndtools.wizards.project;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Map;

import org.bndtools.api.ILogger;
import org.bndtools.api.Logger;
import org.bndtools.utils.workspace.FileUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.internal.ui.wizards.JavaProjectWizard;
import org.eclipse.jdt.ui.wizards.NewJavaProjectWizardPageTwo;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;

import aQute.bnd.build.Project;
import aQute.bnd.build.model.BndEditModel;
import aQute.bnd.properties.Document;
import bndtools.Plugin;
import bndtools.central.Central;
import bndtools.editor.model.BndProject;
import bndtools.versioncontrol.util.VersionControlUtils;

abstract class AbstractNewBndProjectWizard extends JavaProjectWizard {
    private static final ILogger logger = Logger.getLogger(AbstractNewBndProjectWizard.class);

    protected final NewBndProjectWizardPageOne pageOne;
    protected final NewJavaProjectWizardPageTwo pageTwo;

    AbstractNewBndProjectWizard(NewBndProjectWizardPageOne pageOne, NewJavaProjectWizardPageTwo pageTwo) {
        super(pageOne, pageTwo);
        setWindowTitle("New Bnd OSGi Project");
        setNeedsProgressMonitor(true);

        this.pageOne = pageOne;
        this.pageTwo = pageTwo;
    }

    @Override
    public void addPages() {
        addPage(pageOne);
        addPage(pageTwo);
    }

    /**
     * Generate the new Bnd model for the project. This implementation simply returns an empty Bnd model.
     * 
     * @param monitor
     */
    @SuppressWarnings({
            "static-method", "unused"
    })
    protected BndEditModel generateBndModel(IProgressMonitor monitor) {
        return new BndEditModel();
    }

    /**
     * Allows for an IProjectTemplate to modify the new Bnd project
     * 
     * @param monitor
     */
    @SuppressWarnings({
            "static-method", "unused"
    })
    protected BndProject generateBndProject(IProject project, IProgressMonitor monitor) {
        return new BndProject(project);
    }

    /**
     * Modify the newly generated Java project; this method is executed from within a workspace operation so is free to
     * make workspace resource modifications.
     * 
     * @throws CoreException
     */
    protected void processGeneratedProject(BndEditModel bndModel, IJavaProject project, IProgressMonitor monitor) throws CoreException {
        SubMonitor progress = SubMonitor.convert(monitor, 3);

        Document document = new Document("");
        bndModel.saveChangesTo(document);
        progress.worked(1);

        ByteArrayInputStream bndInput;
        try {
            bndInput = new ByteArrayInputStream(document.get().getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            return;
        }
        IFile bndBndFile = project.getProject().getFile(Project.BNDFILE);
        if (bndBndFile.exists()) {
            bndBndFile.setContents(bndInput, false, false, progress.newChild(1));
        } else {
            bndBndFile.create(bndInput, false, progress.newChild(1));
        }

        IFile buildXmlFile = project.getProject().getFile("build.xml");
        InputStream buildXmlInput = getClass().getResourceAsStream("template_bnd_build.xml");

        StringBuilder path_to_workspace = new StringBuilder();
        IPath projPath = project.getProject().getLocation();

        // Grab relative path to workspace.
        // Try #1 -- try to find cnf directory
        try {
            File wsbase = Central.getWorkspace().getBase();
            IPath wspath = new Path(wsbase.getAbsolutePath()); //Central.toPath(wsbase);
            if (wspath.isPrefixOf(projPath)) {
                IPath relative = projPath.makeRelativeTo(wspath);
                for (int i = 0; i < relative.segmentCount(); i++) {
                    if (path_to_workspace.length() > 0)
                        path_to_workspace.append("/");
                    path_to_workspace.append("..");
                }
            }
        } catch (Exception e2) {}

        if (path_to_workspace.length() == 0) {
            // Try #2 -- make it relative to top of eclipse workspace
            if (Platform.getLocation().isPrefixOf(projPath)) {
                IPath relative = projPath.makeRelativeTo(Platform.getLocation());
                for (int i = 0; i < relative.segmentCount(); i++) {
                    if (path_to_workspace.length() > 0)
                        path_to_workspace.append("/");
                    path_to_workspace.append("..");
                }
            } else {
                // Try #3 -- use absolute path
                path_to_workspace.append(Platform.getLocation().toPortableString());
            }
        }

        // Inject a few variables
        InputStreamReader is = new InputStreamReader(buildXmlInput);
        BufferedReader br = new BufferedReader(is);
        StringBuffer sb = new StringBuffer();
        try {
            String read = br.readLine();
            while (read != null) {
                read = read.replaceAll("@PROJECT", project.getElementName());
                read = read.replaceAll("@PATH_TO_WORKSPACE_ROOT", path_to_workspace.toString());
                sb.append(read);
                sb.append("\r\n"); // TODO -- replace with EOL characters.
                read = br.readLine();
            }
        } catch (IOException e) {
            // TODO - what to do
        }

        InputStream buildXmlInputNew = null;
        try {
            buildXmlInputNew = new ByteArrayInputStream(sb.toString().getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            try {
                buildXmlInput.reset();
            } catch (IOException e1) {
                // TODO -- what to do..
            }
            buildXmlInputNew = buildXmlInput;
        }

        try {
            if (buildXmlFile.exists()) {
                buildXmlFile.setContents(buildXmlInputNew, false, false, progress.newChild(1));
            } else {
                buildXmlFile.create(buildXmlInputNew, false, progress.newChild(1));
            }
        } finally {
            try {
                buildXmlInput.close();
            } catch (IOException e) {}
        }

        BndProject proj = generateBndProject(project.getProject(), progress.newChild(1));

        progress.setWorkRemaining(proj.getResources().size());
        for (Map.Entry<String,URL> resource : proj.getResources().entrySet()) {
            importResource(project.getProject(), resource.getKey(), resource.getValue(), progress.newChild(1));
        }

        try {
            VersionControlUtils.createDefaultProjectIgnores(project);
            VersionControlUtils.addToIgnoreFile(project, null, "/generated/");
        } catch (IOException e) {
            logger.logError("Unable to create ignore file(s) for project " + project.getProject().getName(), e);
        }
    }

    protected static IFile importResource(IProject project, String fullPath, URL url, IProgressMonitor monitor) throws CoreException {
        SubMonitor progress = SubMonitor.convert(monitor, 2);

        IFile p = project.getFile(fullPath);
        InputStream is = null;
        try {
            is = url.openStream();

            if (p.exists()) {
                p.setContents(is, false, true, progress.newChild(2, SubMonitor.SUPPRESS_NONE));
            } else {
                FileUtils.recurseCreate(p.getParent(), progress.newChild(1, SubMonitor.SUPPRESS_NONE));
                p.create(is, false, progress.newChild(1, SubMonitor.SUPPRESS_NONE));
            }
        } catch (IOException e) {
            throw new CoreException(new Status(IStatus.ERROR, Plugin.PLUGIN_ID, e.getMessage(), e));
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {}
            }
        }
        return p;
    }

    @Override
    public boolean performFinish() {
        boolean result = super.performFinish();
        if (result) {
            final IJavaProject javaProj = (IJavaProject) getCreatedElement();
            try {
                // Run using the progress bar from the wizard dialog
                getContainer().run(false, false, new IRunnableWithProgress() {
                    public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
                        try {
                            SubMonitor progress = SubMonitor.convert(monitor, 3);

                            // Generate the Bnd model
                            final BndEditModel bndModel = generateBndModel(progress.newChild(1));

                            // Make changes to the project
                            final IWorkspaceRunnable op = new IWorkspaceRunnable() {
                                public void run(IProgressMonitor monitor) throws CoreException {
                                    processGeneratedProject(bndModel, javaProj, monitor);
                                }
                            };
                            javaProj.getProject().getWorkspace().run(op, progress.newChild(2));
                        } catch (CoreException e) {
                            throw new InvocationTargetException(e);
                        }
                    }
                });
                result = true;
            } catch (InvocationTargetException e) {
                ErrorDialog.openError(getShell(), "Error", "", new Status(IStatus.ERROR, Plugin.PLUGIN_ID, 0, MessageFormat.format("Error creating Bnd project descriptor file ({0}).", Project.BNDFILE), e.getTargetException()));
                result = false;
            } catch (InterruptedException e) {
                // Shouldn't happen
            }

            // Open the bnd.bnd file in the editor
            IFile bndFile = javaProj.getProject().getFile(Project.BNDFILE);
            try {
                IDE.openEditor(getWorkbench().getActiveWorkbenchWindow().getActivePage(), bndFile);
            } catch (PartInitException e) {
                ErrorDialog.openError(getShell(), "Error", null, new Status(IStatus.ERROR, Plugin.PLUGIN_ID, 0, MessageFormat.format("Failed to open project descriptor file {0} in the editor.", bndFile.getFullPath().toString()), e));
            }
        }
        return result;
    }
}
