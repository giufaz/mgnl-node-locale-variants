package com.giufaz.magnolia.localevariants.app.activation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import info.magnolia.config.registry.DefinitionMetadata;
import info.magnolia.config.registry.DefinitionProvider;
import info.magnolia.config.source.raw.DefinitionRawViewMapWrapper;
import info.magnolia.map2bean.Map2BeanTransformer;
import info.magnolia.ui.api.app.AppDescriptor;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.giufaz.magnolia.localevariants.config.ResolvedLocaleVariantsAppConfig;

class LocaleVariantsAppsDecoratorTest {

    private final AppDefinitionTransformer transformer = mock(AppDefinitionTransformer.class);
    private final Map2BeanTransformer map2Bean = mock(Map2BeanTransformer.class);
    private final LocaleVariantsAppsDecorator decorator = new LocaleVariantsAppsDecorator(
            Map.of("promos", config("lvd:promo")), transformer, map2Bean);

    @Test
    void test_appliesTo() {
        assertTrue(decorator.appliesTo(provider("promos", true)));
        assertFalse(decorator.appliesTo(provider("pages-app", true)), "not in the module config");
    }

    // An app with configuration problems is left to Magnolia's own reporting
    @Test
    void test_decorate_invalidApp() {
        DefinitionProvider<AppDescriptor> invalid = provider("promos", false);

        assertSame(invalid, decorator.decorate(invalid));
        verifyNoInteractions(transformer, map2Bean);
    }

    // Fail-open: on any error the app stays as it is
    @Test
    void test_decorate_withError() {
        DefinitionProvider<AppDescriptor> promos = provider("promos", true);
        when(transformer.transform(any(), any())).thenThrow(new IllegalStateException("boom"));

        assertSame(promos, decorator.decorate(promos));
    }

    @Test
    void test_decorate_passesTheAppConfig() {
        DefinitionProvider<AppDescriptor> promos = provider("promos", true);
        when(transformer.transform(any(), any())).thenThrow(new IllegalStateException("stop after the transformer"));

        decorator.decorate(promos);

        verify(transformer).transform(Map.of("name", "promos"), config("lvd:promo"));
    }

    @SuppressWarnings("unchecked")
    private static DefinitionProvider<AppDescriptor> provider(String name, boolean valid) {
        DefinitionMetadata metadata = mock(DefinitionMetadata.class);
        when(metadata.getName()).thenReturn(name);
        DefinitionProvider<AppDescriptor> provider = mock(DefinitionProvider.class);
        when(provider.getMetadata()).thenReturn(metadata);
        when(provider.isValid()).thenReturn(valid);
        when(provider.getRaw()).thenReturn(new DefinitionRawViewMapWrapper(Map.of("name", name)));
        return provider;
    }

    private static ResolvedLocaleVariantsAppConfig config(String... nodeTypes) {
        return new ResolvedLocaleVariantsAppConfig("promos", List.of(nodeTypes), List.of("editItem"), List.of("activate"), true);
    }
}
