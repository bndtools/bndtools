package org.bndtools.core.templating.repobased;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.bndtools.templating.Template;
import org.bndtools.templating.TemplateEngine;
import org.bndtools.templating.TemplateLoader;
import org.bndtools.utils.collections.CollectionUtils;
import org.osgi.framework.Constants;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.repository.Repository;
import org.osgi.util.function.Function;
import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.Promises;
import org.osgi.util.promise.Success;

import aQute.bnd.build.Workspace;
import aQute.bnd.http.HttpClient;
import aQute.bnd.osgi.resource.CapReqBuilder;
import aQute.bnd.osgi.resource.ResourceUtils;
import aQute.bnd.osgi.resource.ResourceUtils.IdentityCapability;
import aQute.bnd.repository.osgi.OSGiRepository;
import aQute.bnd.service.RepositoryPlugin;
import aQute.service.reporter.Reporter;
import bndtools.central.Central;
import bndtools.preferences.BndPreferences;

@Component(name = "org.bndtools.templating.repos", property = {
        "source=workspace", Constants.SERVICE_DESCRIPTION + "=Load templates from the Workspace and Repositories", Constants.SERVICE_RANKING + "=" + ReposTemplateLoader.RANKING
})
public class ReposTemplateLoader implements TemplateLoader {

    static final int RANKING = Integer.MAX_VALUE;

    private static final String NS_TEMPLATE = "org.bndtools.template";

    private final ConcurrentMap<String,TemplateEngine> engines = new ConcurrentHashMap<>();

    // for testing
    Workspace workspace = null;

    private ExecutorService executor;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policyOption = ReferencePolicyOption.GREEDY)
    void setExecutorService(ExecutorService executor) {
        this.executor = executor;
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    void addTemplateEngine(TemplateEngine engine, Map<String,Object> svcProps) {
        String name = (String) svcProps.get("name");
        engines.put(name, engine);
    }

    void removeTemplateEngine(@SuppressWarnings("unused") TemplateEngine engine, Map<String,Object> svcProps) {
        String name = (String) svcProps.get("name");
        engines.remove(name);
    }

    @Activate
    void activate() {
        if (executor == null)
            executor = Executors.newCachedThreadPool();
    }

    @Override
    public Promise<List<Template>> findTemplates(String templateType, final Reporter reporter) {
        String filterStr = String.format("(%s=%s)", NS_TEMPLATE, templateType);
        final Requirement requirement = new CapReqBuilder(NS_TEMPLATE).addDirective(Namespace.REQUIREMENT_FILTER_DIRECTIVE, filterStr).buildSyntheticRequirement();

        // Try to get the repositories and BundleLocator from the workspace
        List<Repository> workspaceRepos;
        BundleLocator tmpLocator;
        try {
            if (workspace == null)
                workspace = Central.getWorkspace();
            workspaceRepos = workspace.getPlugins(Repository.class);
            tmpLocator = new RepoPluginsBundleLocator(workspace.getPlugins(RepositoryPlugin.class), workspace.getPlugin(HttpClient.class));
        } catch (Exception e) {
            workspaceRepos = Collections.emptyList();
            tmpLocator = new DirectDownloadBundleLocator();
        }
        final BundleLocator locator = tmpLocator;

        // Setup the repos
        List<Repository> repos = new ArrayList<>(workspaceRepos.size() + 1);
        repos.addAll(workspaceRepos);
        addPreferenceConfiguredRepos(repos, reporter);

        // Generate a Promise<List<Template>> for each repository and add to an accumulator
        Promise<List<Template>> accumulator = Promises.resolved((List<Template>) new LinkedList<Template>());
        for (final Repository repo : repos) {
            final Deferred<List<Template>> deferred = new Deferred<>();

            final Promise<List<Template>> current = deferred.getPromise();
            accumulator = accumulator.then(new Success<List<Template>,List<Template>>() {
                @Override
                public Promise<List<Template>> call(Promise<List<Template>> resolved) throws Exception {
                    final List<Template> prefix = resolved.getValue();
                    return current.map(new Function<List<Template>,List<Template>>() {
                        @Override
                        public List<Template> apply(List<Template> t) {
                            return CollectionUtils.append(prefix, t);
                        }
                    });
                }
            });

            executor.submit(new Runnable() {
                @Override
                public void run() {
                    List<Template> templates = new LinkedList<>();
                    Map<Requirement,Collection<Capability>> providerMap = repo.findProviders(Collections.singleton(requirement));
                    if (providerMap != null) {
                        Collection<Capability> candidates = providerMap.get(requirement);
                        if (candidates != null) {
                            for (Capability cap : candidates) {
                                IdentityCapability idcap = ResourceUtils.getIdentityCapability(cap.getResource());
                                Object id = idcap.getAttributes().get(IdentityNamespace.IDENTITY_NAMESPACE);
                                Object ver = idcap.getAttributes().get(IdentityNamespace.CAPABILITY_VERSION_ATTRIBUTE);
                                try {
                                    String engineName = (String) cap.getAttributes().get("engine");
                                    if (engineName == null)
                                        engineName = "stringtemplate";
                                    TemplateEngine engine = engines.get(engineName);
                                    if (engine != null)
                                        templates.add(new CapabilityBasedTemplate(cap, locator, engine));
                                    else
                                        reporter.error("Error loading template from resource '%s' version %s: no Template Engine available matching '%s'", id, ver, engineName);
                                } catch (Exception e) {
                                    reporter.error("Error loading template from resource '%s' version %s: %s", id, ver, e.getMessage());
                                }
                            }
                        }
                    }
                    deferred.resolve(templates);
                }
            });
        }

        return accumulator;
    }

    private void addPreferenceConfiguredRepos(List<Repository> repos, Reporter reporter) {
        BndPreferences bndPrefs = null;
        try {
            bndPrefs = new BndPreferences();
        } catch (Exception e) {
            // e.printStackTrace();
        }

        if (bndPrefs != null && bndPrefs.getEnableTemplateRepo()) {
            List<String> repoUris = bndPrefs.getTemplateRepoUriList();
            try {
                OSGiRepository prefsRepo = loadRepo(repoUris, reporter);
                repos.add(prefsRepo);
            } catch (Exception ex) {
                reporter.exception(ex, "Error loading preference repository: %s", repoUris);
            }
        }
    }

    private OSGiRepository loadRepo(List<String> uris, Reporter reporter) throws Exception {
        OSGiRepository repo = new OSGiRepository();
        repo.setReporter(reporter);
        repo.setRegistry(workspace);

        StringBuilder sb = new StringBuilder();
        for (Iterator<String> iter = uris.iterator(); iter.hasNext();) {
            sb.append(iter.next());
            if (iter.hasNext())
                sb.append(',');
        }

        Map<String,String> properties = new HashMap<>();
        properties.put("name", "bndtools-preferences-template-repos");
        properties.put("locations", sb.toString());
        repo.setProperties(properties);
        return repo;
    }

}
