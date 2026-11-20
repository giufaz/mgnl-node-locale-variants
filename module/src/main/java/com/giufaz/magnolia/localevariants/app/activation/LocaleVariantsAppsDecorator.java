package com.giufaz.magnolia.localevariants.app.activation;

import static info.magnolia.config.converters.RawDefinitionViewToMapConverter.rawViewToMap;

import info.magnolia.config.registry.DefinitionProvider;
import info.magnolia.config.registry.DefinitionProviderBuilder;
import info.magnolia.config.registry.DefinitionRawView;
import info.magnolia.config.registry.decoration.DefinitionDecorator;
import info.magnolia.config.registry.decoration.DefinitionDecoratorMetadata;
import info.magnolia.config.source.DefinitionProviderWrapperWithProxyFallback;
import info.magnolia.config.source.raw.DefinitionRawViewMapWrapper;
import info.magnolia.map2bean.Map2BeanTransformer;
import info.magnolia.transformer.TransformationResult;
import info.magnolia.ui.api.app.AppDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Java decorator of the apps enabled in the module config: raw definition →
 * {@link AppDefinitionTransformer} → bean, the same steps as Magnolia's YAML
 * decorators ({@code YamlDefinitionDecoratorDelegate}).
 *
 * <p>
 * Immutable: a config change creates a new instance.
 */
class LocaleVariantsAppsDecorator implements DefinitionDecorator<AppDescriptor> {

    private static final Logger log = LoggerFactory.getLogger(LocaleVariantsAppsDecorator.class);
    private static final String MODULE = "node-locale-variants";

    private final Map<String, LocaleVariantsAppConfig> apps;
    private final AppDefinitionTransformer transformer;
    private final Map2BeanTransformer map2Bean;
    private final long createdAt = System.currentTimeMillis();

    LocaleVariantsAppsDecorator(Map<String, LocaleVariantsAppConfig> apps, AppDefinitionTransformer transformer,
            Map2BeanTransformer map2Bean) {
        this.apps = Map.copyOf(apps);
        this.transformer = transformer;
        this.map2Bean = map2Bean;
    }

    @Override
    public DefinitionDecoratorMetadata metadata() {
        return () -> MODULE;
    }

    @Override
    public boolean appliesTo(DefinitionProvider<AppDescriptor> provider) {
        return apps.containsKey(provider.getMetadata().getName());
    }

    // Fail-open: on any error the app stays as it is
    @Override
    @SuppressWarnings("unchecked")
    public DefinitionProvider<AppDescriptor> decorate(DefinitionProvider<AppDescriptor> provider) {
        if (!provider.isValid()) {
            return provider;
        }
        String name = provider.getMetadata().getName();
        try {
            DefinitionRawView raw = provider.getRaw();
            // YAML and decorated definitions usually wrap the map itself: use it,
            // else convert the raw view as Magnolia's YAML decorators do
            Map<String, Object> source = raw instanceof DefinitionRawViewMapWrapper wrapper ? wrapper.getRawMap()
                    : rawViewToMap(raw);
            Map<String, Object> decorated = transformer.transform(source, apps.get(name));
            TransformationResult<AppDescriptor> result = map2Bean.transform(decorated,
                    (Class<AppDescriptor>) provider.getMetadata().getType().baseClass());
            List<DefinitionDecorator<AppDescriptor>> decorators = new ArrayList<>(provider.getDecorators());
            decorators.add(this);
            DefinitionProvider<AppDescriptor> built = DefinitionProviderBuilder.<AppDescriptor>newBuilder()
                    .metadata(provider.getMetadata())
                    .rawView(new DefinitionRawViewMapWrapper(decorated))
                    .decorators(decorators)
                    .withLastModifed(Math.max(provider.getLastModified(), createdAt))
                    .buildFromTransformationResult(result);
            log.debug("Locale variants applied to app {}", name);
            return new DefinitionProviderWrapperWithProxyFallback<>(built, provider.get());
        } catch (RuntimeException e) {
            log.warn("Can't apply locale variants to app {}, leaving it unchanged", name, e);
            return provider;
        }
    }

    @Override
    public String toString() {
        return "Java decorator of " + MODULE + " " + apps.keySet();
    }
}
