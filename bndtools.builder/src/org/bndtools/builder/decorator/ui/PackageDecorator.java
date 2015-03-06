package org.bndtools.builder.decorator.ui;

import org.bndtools.api.ILogger;
import org.bndtools.api.Logger;
import org.bndtools.builder.BndtoolsBuilder;
import org.bndtools.utils.swt.SWTConcurrencyUtil;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.ILightweightLabelDecorator;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import aQute.bnd.build.Project;
import aQute.bnd.header.Attrs;
import aQute.bnd.osgi.Constants;
import aQute.bnd.version.Version;

/**
 * A decorator for {@link IPackageFragment}s that adds an icon if the package is exported by the bundle manifest.
 *
 * @author duckAsteroid
 */
public class PackageDecorator extends LabelProvider implements ILightweightLabelDecorator {
    private static final ILogger logger = Logger.getLogger(PackageDecorator.class);
    private static final String packageDecoratorId = "bndtools.packageDecorator";
    private static final QualifiedName packageDecoratorKey = new QualifiedName(BndtoolsBuilder.PLUGIN_ID, packageDecoratorId);
    private static final String excluded = " <excluded>";
    private final ImageDescriptor exportedIcon = AbstractUIPlugin.imageDescriptorFromPlugin(BndtoolsBuilder.PLUGIN_ID, "icons/plus-decorator.png");
    private final ImageDescriptor excludedIcon = AbstractUIPlugin.imageDescriptorFromPlugin(BndtoolsBuilder.PLUGIN_ID, "icons/excluded_ovr.gif");

    @Override
    public void decorate(Object element, IDecoration decoration) {
        try {
            IPackageFragment pkg = (IPackageFragment) element;
            if (pkg.getKind() != IPackageFragmentRoot.K_SOURCE) {
                return;
            }
            IResource pkgResource = pkg.getCorrespondingResource();
            if (pkgResource == null) {
                return;
            }
            String text = pkgResource.getPersistentProperty(packageDecoratorKey);
            if (text == null) {
                return;
            }
            if (excluded.equals(text)) {
                decoration.addOverlay(excludedIcon);
            } else {
                decoration.addOverlay(exportedIcon);
            }
            decoration.addSuffix(text);
        } catch (CoreException e) {
            logger.logError("Package Decorator error", e);
        }
    }

    public static void updateDecoration(IProject project, Project model) throws Exception {

        if (!project.isOpen()) {
            return;
        }

        IJavaProject javaProject = JavaCore.create(project);
        if (javaProject == null) {
            return; // project is not a java project
        }

        assert model.getExports() != null : "Project has not been build";
        assert model.getContained() != null : "Project has not been build";

        //
        // Establish the path to our output folder. Any source folder outputing to this
        // bin folder is fair game. Note that in general the test folder is not outputing
        // to our output folder so we will not decorate it.
        //

        String bin = model.getProperty(Constants.DEFAULT_PROP_BIN_DIR, Constants.DEFAULT_PROP_BIN_DIR);
        IPath binPath = javaProject.getPath().append(bin);

        //
        // Iterate over the class path entries. Any CPE_SOURCE entry is fair game
        //

        boolean changed = false;

        IClasspathEntry[] cpes = javaProject.getResolvedClasspath(true);
        for (IClasspathEntry cpe : cpes) {
            int entryKind = cpe.getEntryKind();
            if (entryKind == IClasspathEntry.CPE_SOURCE) {

                //
                // We should filter for source folders
                // that output to our bin folder. Not that the
                // output location of a source folder can be null
                // we then use the default project output location.
                //

                IPath outputLocation = cpe.getOutputLocation();
                if (outputLocation == null)
                    outputLocation = javaProject.getOutputLocation();

                //
                // So only when we output to our bin folder do
                // we decorate the source folder.

                if (binPath.equals(outputLocation)) {
                    changed |= decorateSourceFolder(model, javaProject, cpe);
                }
            }
        }
        if (changed) {
            updateUI();
        }
    }

    /*
     * Decorate source folders, just iterate over the roots (not sure why a source
     * folder could have multiple roots, but hey, who cares). We then iterator over
     * its packages.
     */
    private static boolean decorateSourceFolder(Project model, IJavaProject jp, IClasspathEntry cpe) throws JavaModelException {
        boolean changed = false;
        IPackageFragmentRoot[] roots = jp.findPackageFragmentRoots(cpe);
        for (IPackageFragmentRoot root : roots) {
            for (IJavaElement c : root.getChildren()) {
                if (c instanceof IPackageFragment) {
                    changed |= decoratePackage(model, (IPackageFragment) c);
                }
            }
        }
        return changed;
    }

    /*
     * Decorate a package.
     */

    private static boolean decoratePackage(Project model, IPackageFragment c) {
        try {

            String text;

            Attrs attrs = model.getExports().getByFQN(c.getElementName());
            if (attrs != null) {
                //
                // Show the export version
                //
                String version = attrs.getVersion();
                if (version == null)
                    version = Version.emptyVersion.toString();
                text = " " + version;

                //
                // Show if we have an import on the export
                //

                attrs = model.getImports().getByFQN(c.getElementName());
                if (attrs != null) {
                    String versionRange = attrs.getVersion();
                    if (versionRange != null) {
                        text += "\u2194" + versionRange;
                    }
                }
            } else {

                //
                // Either contained or excluded now ...
                // Contained is no decoration -> text is null
                // Excluded will have a constant text that is
                // checked in the decoration.
                //

                attrs = model.getContained().getByFQN(c.getElementName());
                if (attrs != null) {
                    text = null;
                } else {
                    text = excluded;
                }
            }

            IResource resource = c.getResource();
            String value = resource.getPersistentProperty(packageDecoratorKey);
            if (value == text || (value != null && value.equals(text))) {
                return false;
            }
            resource.setPersistentProperty(packageDecoratorKey, text);
            return true;
        } catch (CoreException e) {
            logger.logError("Setting persistent property " + packageDecoratorKey + " on " + c, e);
            return false;
        }
    }

    private static void updateUI() {
        Display display = PlatformUI.getWorkbench().getDisplay();
        SWTConcurrencyUtil.execForDisplay(display, true, new Runnable() {
            @Override
            public void run() {
                PlatformUI.getWorkbench().getDecoratorManager().update(packageDecoratorId);
            }
        });
    }
}
