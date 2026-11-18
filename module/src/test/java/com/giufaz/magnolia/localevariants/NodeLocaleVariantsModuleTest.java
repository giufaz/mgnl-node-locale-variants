package com.giufaz.magnolia.localevariants;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;

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
        assertDoesNotThrow(() -> new NodeLocaleVariantsModule(broken).start(null));
    }
}
