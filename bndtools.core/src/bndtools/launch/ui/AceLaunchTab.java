package bndtools.launch.ui;

import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import bndtools.Plugin;

public class AceLaunchTab extends GenericStackedLaunchTab {
    private Image image = null;


    @Override
    protected ILaunchTabPiece[] createStack() {
        return new ILaunchTabPiece[] {
                new ProjectLaunchTabPiece(),
                new AceConfigLaunchTabPiece()
        };
    }

    public String getName() {
        return "ACE";
    }

    @Override
    public Image getImage() {
        synchronized (this) {
            if (image == null) {
                image = AbstractUIPlugin.imageDescriptorFromPlugin(Plugin.PLUGIN_ID, "/icons/brick.png").createImage();
            }
        }
        return image;
    }

    @Override
    public void dispose() {
        super.dispose();
        synchronized (this) {
            if (image != null)
                image.dispose();
        }
    }
}
