package com.giufaz.magnolia.localevariants.app.activation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import info.magnolia.config.registry.DefinitionMetadata;
import info.magnolia.config.registry.DefinitionProvider;
import info.magnolia.config.registry.Registry;
import info.magnolia.event.EventBus;
import info.magnolia.map2bean.Map2BeanTransformer;
import info.magnolia.ui.api.app.AppDescriptor;
import info.magnolia.ui.api.app.registry.AppDescriptorRegistry;
import info.magnolia.ui.api.app.registry.AppRegistryEvent;
import info.magnolia.ui.api.app.registry.AppRegistryEventType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AppActivationTest {

    private final LocaleVariantsAppDescriptorRegistry registry = mock(LocaleVariantsAppDescriptorRegistry.class);
    private final EventBus systemEventBus = mock(EventBus.class);
    private final AppActivation activation = new AppActivation(registry, mock(Map2BeanTransformer.class), systemEventBus);

    // Every app the registry is asked for exists, with metadata named after it
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(registry.getProvider(anyString())).thenAnswer(invocation -> {
            DefinitionMetadata metadata = mock(DefinitionMetadata.class);
            when(metadata.getName()).thenReturn(invocation.getArgument(0));
            DefinitionProvider<AppDescriptor> provider = mock(DefinitionProvider.class);
            when(provider.getMetadata()).thenReturn(metadata);
            return provider;
        });
    }

    @Test
    void test_applyNow_newApps() {
        activation.applyNow(apps("promos", "lvd:promo"));

        verify(registry).setAppsDecorator(notNull());
        assertEquals(Set.of("promos"), reregisteredApps());
    }

    @Test
    void test_applyNow_sameConfig() {
        activation.applyNow(apps("promos", "lvd:promo"));
        clearInvocations(registry, systemEventBus);

        activation.applyNow(apps("promos", "lvd:promo")); // equal configs, new instances

        verify(registry, never()).setAppsDecorator(any());
        verifyNoInteractions(systemEventBus);
    }

    // Events only for the added, removed and changed apps
    @Test
    void test_applyNow_changedApps() {
        Map<String, LocaleVariantsAppConfig> before = apps("promos", "lvd:promo");
        before.putAll(apps("ctas", "lvd:cta"));
        before.putAll(apps("news", "lvd:news"));
        activation.applyNow(before);
        clearInvocations(registry, systemEventBus);

        Map<String, LocaleVariantsAppConfig> after = apps("promos", "lvd:promo"); // unchanged
        after.putAll(apps("ctas", "lvd:cta", "lvd:ctaLink")); // changed
        after.putAll(apps("events", "lvd:event")); // added; news removed
        activation.applyNow(after);

        assertEquals(Set.of("ctas", "news", "events"), reregisteredApps());
    }

    @Test
    void test_applyNow_noApps() {
        activation.applyNow(apps("promos", "lvd:promo"));
        clearInvocations(registry, systemEventBus);

        activation.applyNow(Map.of());

        verify(registry).setAppsDecorator(null);
        assertEquals(Set.of("promos"), reregisteredApps(), "the removed app goes back to Magnolia's definition");
    }

    @Test
    void test_applyNow_unknownApp() {
        when(registry.getProvider("typo")).thenThrow(new Registry.NoSuchDefinitionException("typo"));

        assertDoesNotThrow(() -> activation.applyNow(apps("typo", "lvd:promo")));
        verify(registry).setAppsDecorator(notNull());
    }

    // Another module mapped its own AppDescriptorRegistry: nothing is decorated
    @Test
    void test_applyNow_foreignRegistry() {
        AppDescriptorRegistry foreign = mock(AppDescriptorRegistry.class);
        AppActivation foreignActivation = new AppActivation(foreign, mock(Map2BeanTransformer.class), systemEventBus);

        assertDoesNotThrow(() -> foreignActivation.applyNow(apps("promos", "lvd:promo")));
        verifyNoInteractions(systemEventBus);
    }

    // Debounced: of two calls in a row, only the last one is applied
    @Test
    void test_apply_debounced() {
        activation.apply(apps("promos", "lvd:promo"));
        activation.apply(apps("ctas", "lvd:cta"));

        verify(registry, timeout(3000)).setAppsDecorator(notNull());
        verify(systemEventBus, after(1500).times(1)).fireEvent(any(AppRegistryEvent.class));
        assertEquals(Set.of("ctas"), reregisteredApps());
    }

    private Set<String> reregisteredApps() {
        ArgumentCaptor<AppRegistryEvent> events = ArgumentCaptor.forClass(AppRegistryEvent.class);
        verify(systemEventBus, atLeast(0)).fireEvent(events.capture());
        events.getAllValues().forEach(event -> assertEquals(AppRegistryEventType.REREGISTERED, event.getEventType()));
        return events.getAllValues().stream().map(event -> event.getAppDescriptorMetadata().getName())
                .collect(Collectors.toSet());
    }

    private static Map<String, LocaleVariantsAppConfig> apps(String name, String... nodeTypes) {
        LocaleVariantsAppConfig config = new LocaleVariantsAppConfig();
        config.setNodeTypes(List.of(nodeTypes));
        Map<String, LocaleVariantsAppConfig> apps = new LinkedHashMap<>();
        apps.put(name, config);
        return apps;
    }
}
