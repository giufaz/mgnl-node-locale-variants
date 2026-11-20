package com.giufaz.magnolia.localevariants.app.activation;

import info.magnolia.config.registry.Registry;
import info.magnolia.event.EventBus;
import info.magnolia.event.SystemEventBus;
import info.magnolia.map2bean.Map2BeanTransformer;
import info.magnolia.ui.api.app.registry.AppDescriptorRegistry;
import info.magnolia.ui.api.app.registry.AppRegistryEvent;
import info.magnolia.ui.api.app.registry.AppRegistryEventType;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Applies locale variants to the apps of the module config ({@code apps}), at
 * module start and after every config change (Magnolia restarts the module),
 * through {@link LocaleVariantsAppDescriptorRegistry}.
 *
 * <p>
 * Debounced: Magnolia restarts the module more than once per config change,
 * the first time with a partially decorated config, so only the last call is
 * applied. The apps are decorated again only when their config changes.
 */
public class AppActivation {

    private static final Logger log = LoggerFactory.getLogger(AppActivation.class);
    private static final long DEBOUNCE_MS = 1000;

    private final AppDescriptorRegistry registry;
    private final Map2BeanTransformer map2Bean;
    private final EventBus systemEventBus;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "node-locale-variants-apps");
        thread.setDaemon(true);
        return thread;
    });
    private ScheduledFuture<?> pending;
    // The app configs applied last, by app name
    private Map<String, LocaleVariantsAppConfig> applied = Map.of();

    @Inject
    public AppActivation(AppDescriptorRegistry registry, Map2BeanTransformer map2Bean,
            @Named(SystemEventBus.NAME) EventBus systemEventBus) {
        this.registry = registry;
        this.map2Bean = map2Bean;
        this.systemEventBus = systemEventBus;
    }

    public synchronized void apply(Map<String, LocaleVariantsAppConfig> apps) {
        // Debounce: cancel the update scheduled by the previous call, if it hasn't run
        // yet
        if (pending != null) {
            pending.cancel(false);
        }
        Map<String, LocaleVariantsAppConfig> snapshot = new LinkedHashMap<>(apps);
        pending = executor.schedule(() -> {
            try {
                applyNow(snapshot);
            } catch (RuntimeException e) {
                log.error("[node-locale-variants] Can't apply locale variants to the apps", e);
            }
        }, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    synchronized void applyNow(Map<String, LocaleVariantsAppConfig> apps) {
        if (!(registry instanceof LocaleVariantsAppDescriptorRegistry ownRegistry)) {
            // Another module mapped its own AppDescriptorRegistry over ours
            log.error("[node-locale-variants] Apps not decorated: the AppDescriptorRegistry is {}, not {}",
                    registry.getClass().getName(), LocaleVariantsAppDescriptorRegistry.class.getName());
            return;
        }
        if (apps.equals(applied)) {
            log.debug("[node-locale-variants] Config changed, apps unchanged: {}", apps);
            return;
        }
        ownRegistry.setAppsDecorator(apps.isEmpty() ? null
                : new LocaleVariantsAppsDecorator(apps, new AppDefinitionTransformer(), map2Bean));

        // Update currently applied config and send an event to Admincentral apps about
        // the change
        Map<String, LocaleVariantsAppConfig> previous = applied;
        applied = Map.copyOf(apps);
        Set<String> names = new HashSet<>(previous.keySet());
        names.addAll(apps.keySet());
        names.stream()
                .filter(name -> !Objects.equals(previous.get(name), apps.get(name)))
                .forEach(this::fireReregistered);
        log.info("[node-locale-variants] Locale variants on apps {}", apps);
    }

    // Running AdminCentral sessions pick up the changed app definition
    private void fireReregistered(String appName) {
        try {
            var metadata = registry.getProvider(appName).getMetadata();
            systemEventBus.fireEvent(new AppRegistryEvent(metadata, AppRegistryEventType.REREGISTERED));
        } catch (Registry.NoSuchDefinitionException e) {
            log.warn("[node-locale-variants] App {} of the module config does not exist", appName);
        }
    }
}
