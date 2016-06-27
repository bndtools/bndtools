package bndtools.central;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bndtools.api.BndtoolsConstants;
import org.bndtools.api.ILogger;
import org.bndtools.api.Logger;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Status;

import aQute.bnd.build.Workspace;

class BndWorkspaceChangeListener implements IResourceChangeListener {

    private static final ILogger logger = Logger.getLogger(BndWorkspaceChangeListener.class);

    private Workspace workspace;

    public BndWorkspaceChangeListener(Workspace workspace) {
        this.workspace = workspace;
    }

    /**
     * Only update this field via {@link Central#updateResourceChangeListener(Workspace)} which is synchronized. It is
     * called AFTER a workspace has been reseted.
     */
    public void setWorkspace(Workspace workspace) {
        this.workspace = workspace;
    }

    @Override
    public void resourceChanged(IResourceChangeEvent event) {
        if (event.getType() != IResourceChangeEvent.POST_CHANGE)
            return;

        IResourceDelta rootDelta = event.getDelta();
        if (workspace != null && isCnfChanged(rootDelta)) {
            workspace.refresh();
        }

        BndWorkspaceChangeResult cnfChangeResult = isCnfProjectCreatedMovedDeleted(rootDelta);

        switch (cnfChangeResult) {
        case ADDED_OR_MOVED :
            Central.resetBndWorkspace();
            break;
        case REMOVED_FROM_ECLIPSE :
            Central.removeBndWorkspace();
            break;
        default :
            throw new IllegalStateException(cnfChangeResult.name() + " not handled!");
        }
    }

    private boolean isCnfChanged(IResourceDelta delta) {

        final AtomicBoolean result = new AtomicBoolean(false);
        try {
            delta.accept(new IResourceDeltaVisitor() {
                @Override
                public boolean visit(IResourceDelta delta) throws CoreException {
                    try {

                        if (!isChangeDelta(delta))
                            return false;

                        IResource resource = delta.getResource();
                        if (resource.getType() == IResource.ROOT || resource.getType() == IResource.PROJECT && resource.getName().equals(Workspace.CNFDIR))
                            return true;

                        if (resource.getType() == IResource.PROJECT)
                            return false;

                        if (resource.getType() == IResource.FOLDER && resource.getName().equals("ext")) {
                            result.set(true);
                            return false;
                        }

                        if (resource.getType() == IResource.FILE) {
                            if (Workspace.BUILDFILE.equals(resource.getName())) {
                                result.set(true);
                                return false;
                            }
                            // Check files included by the -include directive in build.bnd
                            List<File> includedFiles = workspace != null ? workspace.getIncluded() : null;
                            if (includedFiles == null) {
                                return false;
                            }
                            for (File includedFile : includedFiles) {
                                IPath location = resource.getLocation();
                                if (location != null && includedFile.equals(location.toFile())) {
                                    result.set(true);
                                    return false;
                                }
                            }
                        }
                        return true;
                    } catch (Exception e) {
                        throw new CoreException(new Status(Status.ERROR, BndtoolsConstants.CORE_PLUGIN_ID, "During checking project changes", e));
                    }
                }

            });
        } catch (CoreException e) {
            logger.logError("Central.isCnfChanged() failed", e);
        }
        return result.get();
    }

    private BndWorkspaceChangeResult isCnfProjectCreatedMovedDeleted(IResourceDelta delta) {
        final Workspace finalWorkspace = this.workspace;
        final AtomicBoolean workspaceAdded = new AtomicBoolean(false);
        final AtomicBoolean workspaceRemoved = new AtomicBoolean(false);
        try {
            delta.accept(new IResourceDeltaVisitor() {
                @Override
                public boolean visit(IResourceDelta delta) throws CoreException {
                    try {
                        IResource resource = delta.getResource();
                        if (resource.getType() == IResource.PROJECT && resource.getName().equals(Workspace.CNFDIR)) {
                            // found correct change...further checking because otherwise the workspace is reseted more than once
                            if (delta.getKind() == IResourceDelta.REMOVED) {
                                workspaceRemoved.set(true);
                            } else if (finalWorkspace == null || !finalWorkspace.getBase().equals(resource.getLocation().toFile().getParentFile())) {
                                workspaceAdded.set(true);
                            }
                            return false;
                        }
                        return true;
                    } catch (Exception e) {
                        throw new CoreException(new Status(Status.ERROR, BndtoolsConstants.CORE_PLUGIN_ID, "During checking project changes", e));
                    }
                }

            });
        } catch (CoreException e) {
            logger.logError("Central.isCnfProjectCreatedMovedDeleted() failed", e);
        }

        if (workspaceAdded.get()) {
            return BndWorkspaceChangeResult.ADDED_OR_MOVED;
        } else if (workspaceRemoved.get()) {
            return BndWorkspaceChangeResult.REMOVED_FROM_ECLIPSE;
        }
        return BndWorkspaceChangeResult.NOT_RELEVANT;
    }

    private boolean isChangeDelta(IResourceDelta delta) {
        if (IResourceDelta.MARKERS == delta.getFlags())
            return false;
        if ((delta.getKind() & (IResourceDelta.ADDED | IResourceDelta.CHANGED | IResourceDelta.REMOVED)) == 0)
            return false;
        return true;
    }

    private enum BndWorkspaceChangeResult {
        ADDED_OR_MOVED, REMOVED_FROM_ECLIPSE, NOT_RELEVANT
    }

}
