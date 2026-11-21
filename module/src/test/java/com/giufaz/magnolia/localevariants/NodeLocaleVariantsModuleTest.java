package com.giufaz.magnolia.localevariants;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.giufaz.magnolia.localevariants.app.activation.AppActivation;
import com.giufaz.magnolia.localevariants.config.ActionDefaults;
import com.giufaz.magnolia.localevariants.config.LocaleVariantsAppConfig;
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

    // The config goes to the activation as it is: it is checked there, after the
    // debounce
    @Test
    void test_start_passesTheConfigToTheActivation() {
        AppActivation appActivation = mock(AppActivation.class);
        NodeLocaleVariantsModule module = new NodeLocaleVariantsModule(new JcrReferenceResolverDetector(), appActivation);
        Map<String, LocaleVariantsAppConfig> apps = new LinkedHashMap<>();
        apps.put("promos", new LocaleVariantsAppConfig());
        ActionDefaults defaults = new ActionDefaults();
        module.setApps(apps);
        module.setDefaults(defaults);

        module.start(null);

        verify(appActivation).apply(same(apps), same(defaults));
    }

    // "apps:" and "defaults:" with no value in YAML
    @Test
    void test_setters_null() {
        NodeLocaleVariantsModule module = new NodeLocaleVariantsModule(new JcrReferenceResolverDetector(),
                mock(AppActivation.class));
        module.setApps(null);
        module.setDefaults(null);
        assertEquals(Map.of(), module.getApps());
        assertEquals(List.of(), module.getDefaults().getVariantActions());
        assertEquals(List.of(), module.getDefaults().getContainerActions());
    }
}
