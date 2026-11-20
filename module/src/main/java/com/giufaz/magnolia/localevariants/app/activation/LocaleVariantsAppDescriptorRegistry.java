package com.giufaz.magnolia.localevariants.app.activation;

import info.magnolia.config.registry.DefinitionMetadata;
import info.magnolia.config.registry.DefinitionProvider;
import info.magnolia.event.EventBus;
import info.magnolia.event.SystemEventBus;
import info.magnolia.module.ModuleRegistry;
import info.magnolia.ui.api.app.AppDescriptor;
import info.magnolia.ui.api.app.registry.AppDescriptorRegistry;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Magnolia's app registry, type-mapped in the module descriptor: it decorates
 * the providers of the enabled apps on the way out. With no decorator set it
 * behaves exactly like the original.
 */
@Singleton
public class LocaleVariantsAppDescriptorRegistry extends AppDescriptorRegistry {

    private volatile LocaleVariantsAppsDecorator decorator;
    // Magnolia wraps the providers in a new instance on every lookup, so the
    // decorated provider is cached per app, keyed by the source's lastModified
    // and raw view (a YAML change gives new ones)
    private final Map<String, Cached> caches = new ConcurrentHashMap<>();

    // The decorator is part of the key: an entry made with an older decorator
    // (a lookup running while the config changed) is never served
    private record Cached(LocaleVariantsAppsDecorator decorator, long lastModified, Object raw,
            DefinitionProvider<AppDescriptor> provider) {

        boolean isValidFor(LocaleVariantsAppsDecorator current, DefinitionProvider<AppDescriptor> source) {
            return decorator == current && lastModified == source.getLastModified() && raw == source.getRaw();
        }
    }

    @Inject
    public LocaleVariantsAppDescriptorRegistry(@Named(SystemEventBus.NAME) EventBus systemEventBus,
            ModuleRegistry moduleRegistry) {
        super(systemEventBus, moduleRegistry);
    }

    void setAppsDecorator(LocaleVariantsAppsDecorator decorator) {
        this.decorator = decorator;
        caches.clear();
    }

    @Override
    public DefinitionProvider<AppDescriptor> getProvider(DefinitionMetadata id) {
        return decorated(super.getProvider(id));
    }

    @Override
    public DefinitionProvider<AppDescriptor> getProvider(String referenceId) {
        return decorated(super.getProvider(referenceId));
    }

    @Override
    public Collection<DefinitionProvider<AppDescriptor>> getAllProviders() {
        return super.getAllProviders().stream().map(this::decorated).toList();
    }

    private DefinitionProvider<AppDescriptor> decorated(DefinitionProvider<AppDescriptor> provider) {
        LocaleVariantsAppsDecorator current = decorator;
        if (current == null || provider == null || !current.appliesTo(provider)) {
            return provider;
        }
        String name = provider.getMetadata().getName();
        Cached cached = caches.get(name);
        if (cached == null || !cached.isValidFor(current, provider)) {
            cached = new Cached(current, provider.getLastModified(), provider.getRaw(), current.decorate(provider));
            caches.put(name, cached);
        }
        return cached.provider();
    }
}
