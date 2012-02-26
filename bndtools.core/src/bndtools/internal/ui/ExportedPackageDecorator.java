package bndtools.internal.ui;

import java.io.IOException;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.ILightweightLabelDecorator;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import aQute.bnd.build.Project;
import aQute.bnd.build.Workspace;
import bndtools.Plugin;
import bndtools.api.IPersistableBndModel;
import bndtools.editor.model.BndEditModel;
import bndtools.model.clauses.ExportedPackage;
import bndtools.utils.FileUtils;

public class ExportedPackageDecorator extends LabelProvider implements ILightweightLabelDecorator {

    private ImageDescriptor plusIcon;
    
    public ExportedPackageDecorator() {
        super();
        this.plusIcon = AbstractUIPlugin.imageDescriptorFromPlugin(Plugin.PLUGIN_ID, "icons/plus.png");
    }
    
    public void decorate(Object element, IDecoration decoration) {
        if (element instanceof IPackageFragment) {
            IPackageFragment pkg = (IPackageFragment)element;
            IJavaProject javaProject = pkg.getJavaProject();
            IProject project = javaProject.getProject();
            try {
                // Load project file and model
                IFile projectFile = project.getFile(Project.BNDFILE);
                BndEditModel projectModel;
                IDocument projectDocument = FileUtils.readFully(projectFile);
                if (projectDocument == null)
                    projectDocument = new Document();
                projectModel = new BndEditModel();
                projectModel.loadFrom(projectDocument);
                
                List<ExportedPackage> exportedPackages = projectModel.getExportedPackages();
                for(ExportedPackage export : exportedPackages) {
                    if (export.getName().equals(pkg.getElementName())) {
                        decoration.addOverlay(plusIcon);
                        return;
                    }
                }
            }
            catch(Exception e) {
                // Do nothing
                //throw new CoreException(new Status(IStatus.ERROR, Plugin.PLUGIN_ID, 0, e.getMessage(), e));
            }
        }
    }

}
