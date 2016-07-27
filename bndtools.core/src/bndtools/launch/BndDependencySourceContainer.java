package bndtools.launch;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.bndtools.api.ILogger;
import org.bndtools.api.Logger;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.sourcelookup.ISourceContainer;
import org.eclipse.debug.core.sourcelookup.ISourceContainerType;
import org.eclipse.debug.core.sourcelookup.ISourceLookupDirector;
import org.eclipse.debug.core.sourcelookup.containers.CompositeSourceContainer;
import org.eclipse.debug.core.sourcelookup.containers.ExternalArchiveSourceContainer;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.sourcelookup.containers.JavaProjectSourceContainer;

import aQute.bnd.build.Container;
import aQute.bnd.build.Container.TYPE;
import aQute.bnd.build.Project;
import bndtools.launch.util.LaunchUtils;

public class BndDependencySourceContainer extends CompositeSourceContainer {
    private static final ILogger logger = Logger.getLogger(BndDependencySourceContainer.class);

    public static final String TYPE_ID = "org.bndtools.core.launch.sourceContainerTypes.bndDependencies";

    @Override
    public boolean equals(Object obj) {
        return obj instanceof BndDependencySourceContainer;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    protected ILaunchConfiguration getLaunchConfiguration() {
        ISourceLookupDirector director = getDirector();
        if (director != null) {
            return director.getLaunchConfiguration();
        }
        return null;
    }

    @Override
    public String getName() {
        return "Bnd Dependencies";
    }

    @Override
    public ISourceContainerType getType() {
        return getSourceContainerType(TYPE_ID);
    }

    @Override
    protected ISourceContainer[] createSourceContainers() throws CoreException {
        List<ISourceContainer> result = new LinkedList<ISourceContainer>();

        ILaunchConfiguration config = getLaunchConfiguration();
        try {
            Project project = LaunchUtils.getBndProject(config);
            if (project != null) {
                Collection<Container> runbundles = project.getRunbundles();
                for (Container runbundle : runbundles) {
                    if (runbundle.getType() == TYPE.PROJECT) {
                        String targetProjName = runbundle.getProject().getName();
                        IProject targetProj = ResourcesPlugin.getWorkspace().getRoot().getProject(targetProjName);
                        if (targetProj != null) {
                            IJavaProject targetJavaProj = JavaCore.create(targetProj);
                            result.add(new JavaProjectSourceContainer(targetJavaProj));
                        }
                    } else if (runbundle.getType() == TYPE.REPO) {
                        ExternalArchiveSourceContainer container = new ExternalArchiveSourceContainer(runbundle.getFile().toString(), false);
                        result.add(container);
                    }
                }
            }
        } catch (Exception e) {
            logger.logError("Error querying bnd dependency source containers.", e);
        }

        return result.toArray(new ISourceContainer[0]);
    }
}
