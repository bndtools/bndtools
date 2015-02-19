package org.bndtools.builder;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;

import aQute.bnd.build.Project;
import aQute.bnd.osgi.Constants;
import aQute.bnd.osgi.Processor;
import bndtools.central.Central;

class DeltaWrapper {

    private final Project model;
    private final IResourceDelta delta;
    private final BuildLogger log;

    DeltaWrapper(Project model, IResourceDelta delta, BuildLogger log) {
        this.model = model;
        this.delta = delta;
        this.log = log;
    }

    boolean hasCnfChanged() throws Exception {
        if (delta == null) {
            log.basic("Full build because delta for cnf is null");
            return true;
        }

        if (havePropertiesChanged(model.getWorkspace())) {
            log.basic("cnf properties have changed");
            return true;
        }

        return false;
    }

    /*
     * Any change other then src, test, test_bin, or generated is fair game.
     */
    boolean hasProjectChanged() throws Exception {

        if (havePropertiesChanged(model)) {
            log.basic("Properties changed");
            model.refresh();
            return true;
        }

        if (delta == null) {
            log.basic("Full build because delta is null");
            return true;
        }

        final AtomicBoolean result = new AtomicBoolean(false);
        delta.accept(new IResourceDeltaVisitor() {

            @Override
            public boolean visit(IResourceDelta arg0) throws CoreException {

                if ((delta.getKind() & (IResourceDelta.ADDED | IResourceDelta.CHANGED | IResourceDelta.REMOVED)) == 0)
                    return false;

                IResource resource = arg0.getResource();
                if (resource.getType() == IResource.ROOT || resource.getType() == IResource.PROJECT)
                    return true;

                String path = resource.getProjectRelativePath().toString();

                if (resource.getType() == IResource.FOLDER) {
                    if (check(path, model.getProperty(Constants.DEFAULT_PROP_SRC_DIR)) //
                            || check(path, model.getProperty(Constants.DEFAULT_PROP_TESTSRC_DIR)) //
                            || check(path, model.getProperty(Constants.DEFAULT_PROP_TESTBIN_DIR)) //
                            || check(path, model.getProperty(Constants.DEFAULT_PROP_TARGET_DIR))) {
                        return false;

                    }

                }

                if (IResourceDelta.MARKERS == delta.getFlags())
                    return false;

                log.basic("%s changed", resource);
                result.set(true);
                return false;
            }

        });

        return result.get();
    }

    boolean hasBuildfile() throws Exception {
        File f = new File(model.getTarget(), Project.BUILDFILES);
        return has(f);
    }

    private boolean havePropertiesChanged(Processor processor) throws Exception {

        if (has(processor.getPropertiesFile()))
            return true;

        List<File> included = processor.getIncluded();
        if (included == null)
            return false;

        for (File incl : included) {
            if (has(incl))
                return true;
        }

        return false;
    }

    private boolean has(File f) throws Exception {
        if (f == null)
            return false;

        IPath path = Central.toPath(f);
        if (delta == null)
            return false;
        IPath relativePath = path.makeRelativeTo(delta.getFullPath());
        if (relativePath == null)
            return false;

        IResourceDelta delta = this.delta.findMember(relativePath);
        if (delta == null)
            return false;

        if (delta.getKind() == IResourceDelta.ADDED || delta.getKind() == IResourceDelta.CHANGED || delta.getKind() == IResourceDelta.REMOVED)
            return true;

        return false;
    }

    static boolean check(String changed, String prefix) {
        if (changed.equals(prefix))
            return true;

        if (changed.length() <= prefix.length())
            return false;

        char c = changed.charAt(prefix.length());
        if (c == '/' && changed.startsWith(prefix))
            return true;

        return false;
    }

    public boolean isTestBin(IResource resource) {
        String path = resource.getProjectRelativePath().toString();
        return check(path, model.getProperty(Constants.DEFAULT_PROP_TESTSRC_DIR));
    }
}