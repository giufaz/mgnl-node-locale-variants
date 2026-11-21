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

import com.giufaz.magnolia.localevariants.config.ActionDefaults;
import com.giufaz.magnolia.localevariants.config.LocaleVariantsAppConfig;
import com.giufaz.magnolia.localevariants.config.ResolvedLocaleVariantsAppConfig;

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
        Map<String, ResolvedLocaleVariantsAppConfig> before = apps("promos", "lvd:promo");
        before.putAll(apps("ctas", "lvd:cta"));
        before.putAll(apps("news", "lvd:news"));
        activation.applyNow(before);
        clearInvocations(registry, systemEventBus);

        Map<String, ResolvedLocaleVariantsAppConfig> after = apps("promos", "lvd:promo"); // unchanged
        after.putAll(apps("ctas", "lvd:cta", "lvd:ctaLink")); // changed
        after.putAll(apps("events", "lvd:event")); // added; news removed
        activation.applyNow(after);

        assertEquals(Set.of("ctas", "news", "events"), reregisteredApps());
    }

    // A change of the action lists alone is a change of the app
    @Test
    void test_applyNow_changedActionLists() {
        activation.applyNow(apps("promos", "lvd:promo"));
        clearInvocations(registry, systemEventBus);

        activation.applyNow(Map.of("promos", new ResolvedLocaleVariantsAppConfig("promos", List.of("lvd:promo"),
                List.of("editItem", "export"), List.of("activate"), true)));

        verify(registry).setAppsDecorator(notNull());
        assertEquals(Set.of("promos"), reregisteredApps());
    }

    // What the availability rules read: the apps applied last
    @Test
    void test_getAppliedApp() {
        assertNull(activation.getAppliedApp("promos"), "nothing applied yet");

        Map<String, ResolvedLocaleVariantsAppConfig> apps = apps("promos", "lvd:promo");
        activation.applyNow(apps);
        assertEquals(apps.get("promos"), activation.getAppliedApp("promos"));
        assertNull(activation.getAppliedApp("ctas"), "not in the module config");

        activation.applyNow(Map.of());
        assertNull(activation.getAppliedApp("promos"), "removed from the module config");
    }

    // Scheduled but not applied yet: the rules still read the previous config
    @Test
    void test_getAppliedApp_beforeTheDebounce() {
        activation.apply(Map.of("promos", app(true, "lvd:promo")), new ActionDefaults());

        assertNull(activation.getAppliedApp("promos"));
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
        assertNull(foreignActivation.getAppliedApp("promos"), "not applied: the rules allow nothing");
    }

    // Debounced: of two calls in a row, only the last one is applied
    @Test
    void test_apply_debounced() {
        activation.apply(Map.of("promos", app(true, "lvd:promo")), new ActionDefaults());
        activation.apply(Map.of("ctas", app(true, "lvd:cta")), new ActionDefaults());

        verify(registry, timeout(3000)).setAppsDecorator(notNull());
        verify(systemEventBus, after(1500).times(1)).fireEvent(any(AppRegistryEvent.class));
        assertEquals(Set.of("ctas"), reregisteredApps());
    }

    // The config is checked and resolved after the debounce: the first call, with
    // a partially decorated config (no node types yet), is never looked at
    @Test
    void test_apply_checksOnlyTheLastConfig() {
        LocaleVariantsAppConfig partial = app(true);
        partial.setContainerActions(List.of("export"));
        LocaleVariantsAppConfig complete = app(true, "lvd:promo");
        complete.setContainerActions(List.of("export"));
        ActionDefaults defaults = new ActionDefaults();
        defaults.setVariantActions(List.of("editItem"));

        activation.apply(Map.of("promos", partial), defaults);
        activation.apply(Map.of("promos", complete), defaults);

        verify(registry, timeout(3000)).setAppsDecorator(notNull());
        verify(registry, after(1500).times(1)).setAppsDecorator(any());
        assertEquals(new ResolvedLocaleVariantsAppConfig("promos", List.of("lvd:promo"), List.of("editItem"),
                List.of("export"), true), activation.getAppliedApp("promos"));
    }

    // Only the eligible apps are decorated: enabled, with node types
    @Test
    void test_resolveEligibleApps() {
        Map<String, LocaleVariantsAppConfig> apps = new LinkedHashMap<>();
        apps.put("promos", app(true, "lvd:promo"));
        apps.put("pages-app", app(false, "mgnl:page")); // disabled
        apps.put("ctas", app(true)); // no node types
        apps.put("empty", null); // "empty:" in YAML

        assertEquals(List.of("promos"),
                List.copyOf(AppActivation.resolveEligibleApps(apps, new ActionDefaults()).keySet()));
    }

    @Test
    void test_resolveEligibleApps_noApps() {
        assertEquals(Map.of(), AppActivation.resolveEligibleApps(new LinkedHashMap<>(), new ActionDefaults()));
    }

    // Their action lists with the module config defaults
    @Test
    void test_resolveEligibleApps_resolvesActionLists() {
        LocaleVariantsAppConfig promos = app(true, "lvd:promo");
        promos.setVariantActions(List.of("editPromo"));
        LocaleVariantsAppConfig ctas = app(true, "lvd:cta");
        ctas.setContainerActions(List.of("exportCta"));
        ctas.setReplaceDefaultContainerActions(true);
        Map<String, LocaleVariantsAppConfig> apps = new LinkedHashMap<>();
        apps.put("promos", promos);
        apps.put("ctas", ctas);
        ActionDefaults defaults = new ActionDefaults();
        defaults.setVariantActions(List.of("editItem"));
        defaults.setContainerActions(List.of("activate"));

        Map<String, ResolvedLocaleVariantsAppConfig> resolved = AppActivation.resolveEligibleApps(apps, defaults);

        assertEquals(new ResolvedLocaleVariantsAppConfig("promos", List.of("lvd:promo"), List.of("editItem", "editPromo"),
                List.of("activate"), true), resolved.get("promos"));
        assertEquals(new ResolvedLocaleVariantsAppConfig("ctas", List.of("lvd:cta"), List.of("editItem"),
                List.of("exportCta"), true), resolved.get("ctas"));
    }

    private Set<String> reregisteredApps() {
        ArgumentCaptor<AppRegistryEvent> events = ArgumentCaptor.forClass(AppRegistryEvent.class);
        verify(systemEventBus, atLeast(0)).fireEvent(events.capture());
        events.getAllValues().forEach(event -> assertEquals(AppRegistryEventType.REREGISTERED, event.getEventType()));
        return events.getAllValues().stream().map(event -> event.getAppDescriptorMetadata().getName())
                .collect(Collectors.toSet());
    }

    // Mutable, with the same action lists for every app
    private static Map<String, ResolvedLocaleVariantsAppConfig> apps(String name, String... nodeTypes) {
        Map<String, ResolvedLocaleVariantsAppConfig> apps = new LinkedHashMap<>();
        apps.put(name, new ResolvedLocaleVariantsAppConfig(name, List.of(nodeTypes), List.of("editItem"), List.of("activate"), true));
        return apps;
    }

    private static LocaleVariantsAppConfig app(boolean enabled, String... nodeTypes) {
        LocaleVariantsAppConfig app = new LocaleVariantsAppConfig();
        app.setEnabled(enabled);
        app.setNodeTypes(List.of(nodeTypes));
        return app;
    }
}
