package com.giufaz.magnolia.localevariants.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import info.magnolia.rest.reference.jcr.JcrReferenceResolver;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class JcrReferenceResolverDetectorTest {

    private final JcrReferenceResolverDetector detector = new JcrReferenceResolverDetector();

    // Fails the build if a Magnolia update changes how JCR reference resolvers are wrapped
    @Test
    void test_checkMagnoliaInternals_withBomMagnolia() {
        assertEquals(Optional.empty(), detector.checkMagnoliaInternals());
    }

    @Test
    void test_isJcrReferenceResolver_onlyJcrResolvers() {
        assertTrue(detector.isJcrReferenceResolver(mock(JcrReferenceResolver.class)));
        assertFalse(detector.isJcrReferenceResolver(item -> Optional.empty()), "custom resolver");
    }
}
