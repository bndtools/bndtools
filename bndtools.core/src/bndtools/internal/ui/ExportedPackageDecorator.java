package bndtools.internal.ui;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.ILightweightLabelDecorator;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import bndtools.Plugin;

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
            
            decoration.addOverlay(plusIcon);
        }
    }

}
