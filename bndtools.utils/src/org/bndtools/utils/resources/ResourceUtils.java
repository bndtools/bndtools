package org.bndtools.utils.resources;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

import org.osgi.framework.Version;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.ExecutionEnvironmentNamespace;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.namespace.contract.ContractNamespace;
import org.osgi.namespace.extender.ExtenderNamespace;
import org.osgi.namespace.service.ServiceNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Resource;
import org.osgi.service.repository.ContentNamespace;

public final class ResourceUtils {

    public static Capability getIdentityCapability(Resource resource) throws IllegalArgumentException {
        List<Capability> caps = resource.getCapabilities(IdentityNamespace.IDENTITY_NAMESPACE);
        if (caps == null || caps.isEmpty())
            throw new IllegalArgumentException("Resource has no identity");
        if (caps.size() > 1)
            throw new IllegalArgumentException("Resource is schizophrenic");
        return caps.get(0);
    }

    public static String getIdentity(Capability identityCapability) throws IllegalArgumentException {
        String id = (String) identityCapability.getAttributes().get(IdentityNamespace.IDENTITY_NAMESPACE);
        if (id == null)
            throw new IllegalArgumentException("Resource identity capability has missing identity attribute");
        return id;
    }

    public static final Version getVersion(Capability identityCapability) throws IllegalArgumentException {
        Object versionObj = identityCapability.getAttributes().get(IdentityNamespace.CAPABILITY_VERSION_ATTRIBUTE);
        if (versionObj instanceof Version)
            return (Version) versionObj;

        if (versionObj == null || versionObj instanceof String)
            return Version.parseVersion((String) versionObj);

        throw new IllegalArgumentException("Resource identity capability has version attribute with incorrect type: " + versionObj.getClass());
    }

    public static final String getIdentity(Resource resource) throws IllegalArgumentException {
        return getIdentity(getIdentityCapability(resource));
    }

    public static final Version getVersion(Resource resource) throws IllegalArgumentException {
        return getVersion(getIdentityCapability(resource));
    }

    public static Capability getContentCapability(Resource resource) throws IllegalArgumentException {
        List<Capability> caps = resource.getCapabilities(ContentNamespace.CONTENT_NAMESPACE);
        if (caps == null || caps.isEmpty())
            throw new IllegalArgumentException("Resource has no content");
        if (caps.size() > 1)
            throw new IllegalArgumentException("Resource is schizophrenic");
        return caps.get(0);
    }

    public static URI getURI(Capability contentCapability) {
        Object uriObj = contentCapability.getAttributes().get(ContentNamespace.CAPABILITY_URL_ATTRIBUTE);
        if (uriObj == null)
            throw new IllegalArgumentException("Resource content capability has missing URL attribute");

        if (uriObj instanceof URI)
            return (URI) uriObj;

        try {
            if (uriObj instanceof URL)
                return ((URL) uriObj).toURI();

            if (uriObj instanceof String) {
                try {
                    URL url = new URL((String) uriObj);
                    return url.toURI();
                } catch (MalformedURLException mfue) {
                    // Ignore
                }

                File f = new File((String) uriObj);
                if (f.isFile()) {
                    return f.toURI();
                }
                return new URI((String) uriObj);
            }

        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Resource content capability has illegal URL attribute", e);
        }

        throw new IllegalArgumentException("Resource content capability has URL attribute with incorrect type: " + uriObj.getClass());
    }

    public static String getVersionAttributeForNamespace(String ns) {
        String name;

        if (IdentityNamespace.IDENTITY_NAMESPACE.equals(ns))
            name = IdentityNamespace.CAPABILITY_VERSION_ATTRIBUTE;
        else if (BundleNamespace.BUNDLE_NAMESPACE.equals(ns))
            name = BundleNamespace.CAPABILITY_BUNDLE_VERSION_ATTRIBUTE;
        else if (HostNamespace.HOST_NAMESPACE.equals(ns))
            name = HostNamespace.CAPABILITY_BUNDLE_VERSION_ATTRIBUTE;
        else if (PackageNamespace.PACKAGE_NAMESPACE.equals(ns))
            name = PackageNamespace.CAPABILITY_VERSION_ATTRIBUTE;
        else if (ServiceNamespace.SERVICE_NAMESPACE.equals(ns))
            name = null;
        else if (ExecutionEnvironmentNamespace.EXECUTION_ENVIRONMENT_NAMESPACE.equals(ns))
            name = ExecutionEnvironmentNamespace.CAPABILITY_VERSION_ATTRIBUTE;
        else if (ExtenderNamespace.EXTENDER_NAMESPACE.equals(ns))
            name = ExtenderNamespace.CAPABILITY_VERSION_ATTRIBUTE;
        else if (ContractNamespace.CONTRACT_NAMESPACE.equals(ns))
            name = ContractNamespace.CAPABILITY_VERSION_ATTRIBUTE;
        else
            name = null;

        return name;
    }

}
