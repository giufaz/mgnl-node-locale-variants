package com.giufaz.magnolia.localevariants;

import static com.giufaz.magnolia.localevariants.delivery.JcrReferenceResolverDetector.JCR_RESOLVER_WRAPPER;

import info.magnolia.module.ModuleLifecycle;
import info.magnolia.module.ModuleLifecycleContext;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

import com.giufaz.magnolia.localevariants.app.activation.AppActivation;
import com.giufaz.magnolia.localevariants.app.activation.LocaleVariantsAppConfig;
import com.giufaz.magnolia.localevariants.delivery.JcrReferenceResolverDetector;

import jakarta.inject.Inject;

/**
 * Locale Variants startup checks and the module
 * config ({@code config.yaml}) application.
 * 
 * Update the config via decoration
 * ({@code <your-module>/decorations/node-locale-variants/config.yaml}).
 *
 */
public class NodeLocaleVariantsModule implements ModuleLifecycle {

    private static final Logger log = LoggerFactory.getLogger(NodeLocaleVariantsModule.class);

    private final JcrReferenceResolverDetector detector;
    private final AppActivation appActivation;

    private Map<String, LocaleVariantsAppConfig> configuredApps = new LinkedHashMap<>();

    @Inject
    public NodeLocaleVariantsModule(JcrReferenceResolverDetector detector, AppActivation appActivation) {
        this.detector = detector;
        this.appActivation = appActivation;
    }

    // Also called after every config change: Magnolia restarts the module
    @Override
    public void start(ModuleLifecycleContext moduleLifecycleContext) {
        // Early warning if a Magnolia changes the internals the reference
        // localization relies on
        detector.checkMagnoliaInternals().ifPresent(problem -> log.error(referencesDisabledMessage(problem)));
        appActivation.apply(getAppsEligibleForLocaleVariants());
    }

    private Map<String, LocaleVariantsAppConfig> getAppsEligibleForLocaleVariants() {
        Map<String, LocaleVariantsAppConfig> eligible = new LinkedHashMap<>();
        configuredApps.forEach((name, app) -> {
            if (isEligibleForLocaleVariants(name, app)) {
                eligible.put(name, app);
            }
        });
        return eligible;
    }

    private boolean isEligibleForLocaleVariants(String name, LocaleVariantsAppConfig app) {
        if (app != null && !app.isEnabled()) {
            log.debug("[node-locale-variants] App {} disabled (apps.{}.enabled: false)", name, name);
            return false;
        }
        if (app == null || app.getNodeTypes().isEmpty()) {
            log.warn("[node-locale-variants] App {} not decorated: apps.{}.nodeTypes is required", name, name);
            return false;
        }
        return true;
    }

    // Bean property "apps": the key of the module config (config.yaml)
    public Map<String, LocaleVariantsAppConfig> getApps() {
        return configuredApps;
    }

    public void setApps(Map<String, LocaleVariantsAppConfig> apps) {
        this.configuredApps = apps == null ? new LinkedHashMap<>() : apps;
    }

    static String referencesDisabledMessage(String problem) {
        String component = JcrReferenceResolverDetector.class.getSimpleName();
        var msgArgs = new Object[] { JCR_RESOLVER_WRAPPER, component, problem };
        return MessageFormatter.arrayFormat(REFERENCES_DISABLED_MSG, msgArgs).getMessage();
    }

    private static final String REFERENCES_DISABLED_MSG = """
            [node-locale-variants] Delivery Endpoint node references won't be served as locale variants:
              Magnolia changed or removed its internal class ({}).
              Fix: update mgnl-node-locale-variants module if there is a new version,
              or override the component {} in your module descriptor with a custom version.
              Check the javadoc of the component to see how to override its methods.
              More info about the issue: {}""";
}
