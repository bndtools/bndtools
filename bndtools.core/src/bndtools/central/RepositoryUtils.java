package bndtools.central;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import org.bndtools.api.ILogger;
import org.bndtools.api.Logger;
import org.bndtools.utils.repos.RepoUtils;

import aQute.bnd.build.Workspace;
import aQute.bnd.build.model.clauses.VersionedClause;
import aQute.bnd.header.Attrs;
import aQute.bnd.osgi.Constants;
import aQute.bnd.service.RegistryPlugin;
import aQute.bnd.service.RepositoryPlugin;
import bndtools.model.repo.DependencyPhase;
import bndtools.model.repo.RepositoryBundle;
import bndtools.model.repo.RepositoryBundleVersion;

public class RepositoryUtils {
    private static final ILogger logger = Logger.getLogger(RepositoryUtils.class);

    private static final String CACHE_REPO = "cache";
    private static final String VERSION_LATEST = "latest";

    public static List<RepositoryPlugin> listRepositories(boolean hideCache) {
        Workspace workspace;
        try {
            workspace = Central.getWorkspace();
            if (workspace == null) {
                return Collections.emptyList();
            }
        } catch (Exception e1) {
            return Collections.emptyList();
        }
        return listRepositories(workspace, hideCache);
    }

    public static List<RepositoryPlugin> listRepositories(final Workspace localWorkspace, final boolean hideCache) {
        try {
            return Central.bndCall(new Callable<List<RepositoryPlugin>>() {
                @Override
                public List<RepositoryPlugin> call() throws Exception {
                    List<RepositoryPlugin> plugins = new ArrayList<>();
                    if (localWorkspace != null) {
                        plugins.addAll(localWorkspace.getPlugins(RepositoryPlugin.class));
                    }
                    List<RepositoryPlugin> repos = new ArrayList<RepositoryPlugin>(plugins.size() + 1);

                    // Add the workspace repo if the provided workspace == the global bnd workspace
                    Workspace bndWorkspace = Central.getWorkspaceIfPresent();
                    if (bndWorkspace == localWorkspace)
                        repos.add(Central.getWorkspaceRepository());

                    // Add the repos from the provided workspace
                    for (RepositoryPlugin plugin : plugins) {
                        if (!hideCache || !CACHE_REPO.equals(plugin.getName()))
                            repos.add(plugin);
                    }

                    for (RepositoryPlugin repo : repos) {
                        if (repo instanceof RegistryPlugin) {
                            RegistryPlugin registry = (RegistryPlugin) repo;
                            registry.setRegistry(bndWorkspace);
                        }
                    }

                    return repos;
                }
            });
        } catch (Exception e) {
            logger.logError("Error loading repositories: " + e.getMessage(), e);
        }
        return Collections.emptyList();
    }

    public static VersionedClause convertRepoBundle(RepositoryBundle bundle) {
        Attrs attribs = new Attrs();
        if (RepoUtils.isWorkspaceRepo(bundle.getRepo())) {
            attribs.put(Constants.VERSION_ATTRIBUTE, VERSION_LATEST);
        }
        return new VersionedClause(bundle.getBsn(), attribs);
    }

    public static VersionedClause convertRepoBundleVersion(RepositoryBundleVersion bundleVersion, DependencyPhase phase) {
        Attrs attribs = new Attrs();
        if (RepoUtils.isWorkspaceRepo(bundleVersion.getParentBundle().getRepo()))
            attribs.put(Constants.VERSION_ATTRIBUTE, VERSION_LATEST);
        else {
            StringBuilder builder = new StringBuilder();

            builder.append(bundleVersion.getVersion().getMajor());
            builder.append('.').append(bundleVersion.getVersion().getMinor());
            if (phase != DependencyPhase.Build)
                builder.append('.').append(bundleVersion.getVersion().getMicro());

            attribs.put(Constants.VERSION_ATTRIBUTE, builder.toString());
        }
        return new VersionedClause(bundleVersion.getParentBundle().getBsn(), attribs);
    }

}
