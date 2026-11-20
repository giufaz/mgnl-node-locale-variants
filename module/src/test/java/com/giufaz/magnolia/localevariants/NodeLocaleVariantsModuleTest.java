package com.giufaz.magnolia.localevariants;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.giufaz.magnolia.localevariants.app.activation.AppActivation;
import com.giufaz.magnolia.localevariants.app.activation.LocaleVariantsAppConfig;
import com.giufaz.magnolia.localevariants.delivery.JcrReferenceResolverDetector;

class NodeLocaleVariantsModuleTest {

    @Test
    void test_referencesDisabledMessage_namesClassComponentAndProblem() {
        String message = NodeLocaleVariantsModule.referencesDisabledMessage("simulated problem");
        assertTrue(message.contains("internal class (" + JcrReferenceResolverDetector.JCR_RESOLVER_WRAPPER + ")"), message);
        assertTrue(message.contains("override the component " + JcrReferenceResolverDetector.class.getSimpleName()), message);
        assertTrue(message.endsWith("More info about the issue: simulated problem"), message);
        assertFalse(message.contains("{}"), "every placeholder filled: " + message);
    }

    @Test
    void test_start_withFailingCheck() {
        JcrReferenceResolverDetector broken = new JcrReferenceResolverDetector() {
            @Override
            public Optional<String> checkMagnoliaInternals() {
                return Optional.of("simulated problem");
            }
        };
        assertDoesNotThrow(() -> new NodeLocaleVariantsModule(broken, mock(AppActivation.class)).start(null));
    }

    // Only the eligible apps reach the activation: enabled, with node types
    @Test
    void test_start_eligibleApps() {
        Map<String, LocaleVariantsAppConfig> apps = new LinkedHashMap<>();
        apps.put("promos", app(true, "lvd:promo"));
        apps.put("pages-app", app(false, "mgnl:page")); // disabled
        apps.put("ctas", app(true)); // no node types
        apps.put("empty", null); // "empty:" in YAML

        assertEquals(List.of("promos"), List.copyOf(appliedApps(apps).keySet()));
    }

    @Test
    void test_start_noApps() {
        assertEquals(Map.of(), appliedApps(new LinkedHashMap<>()));
    }

    @Test
    void test_setApps_null() {
        NodeLocaleVariantsModule module = new NodeLocaleVariantsModule(new JcrReferenceResolverDetector(),
                mock(AppActivation.class));
        module.setApps(null);
        assertEquals(Map.of(), module.getApps());
    }

    // Starts a module configured with apps and returns what it passes to the activation
    @SuppressWarnings("unchecked")
    private static Map<String, LocaleVariantsAppConfig> appliedApps(Map<String, LocaleVariantsAppConfig> apps) {
        AppActivation appActivation = mock(AppActivation.class);
        NodeLocaleVariantsModule module = new NodeLocaleVariantsModule(new JcrReferenceResolverDetector(), appActivation);
        module.setApps(apps);
        module.start(null);
        ArgumentCaptor<Map<String, LocaleVariantsAppConfig>> applied = ArgumentCaptor.forClass(Map.class);
        verify(appActivation).apply(applied.capture());
        return applied.getValue();
    }

    private static LocaleVariantsAppConfig app(boolean enabled, String... nodeTypes) {
        LocaleVariantsAppConfig app = new LocaleVariantsAppConfig();
        app.setEnabled(enabled);
        app.setNodeTypes(List.of(nodeTypes));
        return app;
    }
}
