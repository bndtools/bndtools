package org.bndtools.builder.handlers.bndpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bndtools.api.BndtoolsConstants;
import org.bndtools.build.api.AbstractBuildErrorDetailsHandler;
import org.bndtools.build.api.DefaultBuildErrorDetailsHandler;
import org.bndtools.build.api.MarkerData;
import org.bndtools.builder.BndtoolsBuilder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.ui.IMarkerResolution;
import aQute.bnd.build.Project;
import aQute.service.reporter.Report.Location;

public class SourceFolderClasspathEntryProblemHandler extends AbstractBuildErrorDetailsHandler {

    @Override
    public List<MarkerData> generateMarkerData(IProject project, Project model, Location location) throws Exception {
        MarkerData markerData = DefaultBuildErrorDetailsHandler.getMarkerData(project, model, location);

        return Collections.singletonList(new MarkerData(project, markerData.getAttribs(), true));
    }

    @Override
    public List<IMarkerResolution> getResolutions(final IMarker marker) {
        final String context = marker.getAttribute(BndtoolsConstants.BNDTOOLS_MARKER_CONTEXT_ATTR, "");

        IMarkerResolution markerResolution = new IMarkerResolution() {
            @Override
            public void run(final IMarker marker) {
                new Job("Correcting source folder classpath entry") {
                    @Override
                    protected IStatus run(IProgressMonitor monitor) {
                        IProject project = marker.getResource().getProject();
                        IJavaProject javaProject = JavaCore.create(project);

                        try {
                            correctSourceFolderClasspathEntry(javaProject, context, monitor);
                        } catch (Exception e) {
                            return new Status(IStatus.ERROR, BndtoolsBuilder.PLUGIN_ID, this.getName(), e);
                        }

                        return Status.OK_STATUS;
                    }

                }.schedule();
            }

            @Override
            public String getLabel() {
                final String label;

                if (context != null) {
                    label = "'" + context + "' ";
                } else {
                    label = "";
                }

                return "Correct the source folder " + label + "classpath entry by removing custom output location";
            }
        };

        return Collections.singletonList(markerResolution);
    }

    private void correctSourceFolderClasspathEntry(IJavaProject javaProject, String context, IProgressMonitor monitor) throws JavaModelException {
        final List<IClasspathEntry> newClasspathEntries = new ArrayList<>();
        final IClasspathEntry[] existingClasspathEntries = javaProject.getRawClasspath();

        for (IClasspathEntry cpe : existingClasspathEntries) {
            if (cpe.getEntryKind() == IClasspathEntry.CPE_SOURCE && cpe.getPath().toString().equals(context)) {
                IClasspathEntry fixedSourceEntry = JavaCore.newSourceEntry(cpe.getPath(), cpe.getInclusionPatterns(), cpe.getExclusionPatterns(), null, cpe.getExtraAttributes());
                newClasspathEntries.add(fixedSourceEntry);
            } else {
                newClasspathEntries.add(cpe);
            }
        }

        javaProject.setRawClasspath(newClasspathEntries.toArray(new IClasspathEntry[0]), monitor);
    }
}
