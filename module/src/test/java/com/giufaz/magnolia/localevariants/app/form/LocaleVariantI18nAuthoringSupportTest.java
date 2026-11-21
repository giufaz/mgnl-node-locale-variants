package com.giufaz.magnolia.localevariants.app.form;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import info.magnolia.module.site.SiteManager;
import info.magnolia.test.mock.jcr.MockNode;
import info.magnolia.ui.api.i18n.I18NAuthoringSupport;

import java.util.List;
import java.util.Locale;

import javax.jcr.Node;

import org.junit.jupiter.api.Test;

import com.giufaz.magnolia.localevariants.LocaleVariantStructure;

class LocaleVariantI18nAuthoringSupportTest {

    private static final Locale DE_CH = new Locale("de", "CH");

    @SuppressWarnings("unchecked")
    private final I18NAuthoringSupport<Object> delegate = mock(I18NAuthoringSupport.class);
    private final LocaleVariantStructure variantStructure = new LocaleVariantStructure(mock(SiteManager.class));
    private final LocaleVariantI18nAuthoringSupport support = new LocaleVariantI18nAuthoringSupport(delegate,
            variantStructure, () -> true);

    // A variant and the nodes under it: only the variant's locale, base
    // property names
    @Test
    void test_variantLocale_onVariantAndItsNodes() throws Exception {
        Node variant = variant("variant_de_CH");
        Node component = variant.addNode("main", "mgnl:area").addNode("c0", "mgnl:component");

        for (Node node : List.of(variant, component)) {
            assertEquals(List.of(DE_CH), support.getAvailableLocales(node));
            assertFalse(support.getAvailableLocales(node).contains(null), "contains(null) must not throw");
            assertEquals(DE_CH, support.getDefaultLocale(node));
            assertEquals("title", support.deriveLocalisedPropertyName(node, "title", DE_CH));
            assertTrue(support.isDefaultLocale(DE_CH, node));
            assertFalse(support.isDefaultLocale(Locale.ENGLISH, node));
        }
        verifyNoInteractions(delegate);
    }

    @Test
    void test_variantLocale_inAnAppWithoutLocaleVariants() throws Exception {
        Node variant = variant("variant_de");
        LocaleVariantI18nAuthoringSupport otherApp = new LocaleVariantI18nAuthoringSupport(delegate, variantStructure,
                () -> false);
        when(delegate.getAvailableLocales(variant)).thenReturn(List.of(Locale.ENGLISH, Locale.GERMAN));
        when(delegate.deriveLocalisedPropertyName(variant, "title", Locale.GERMAN)).thenReturn("title_de");

        assertEquals(List.of(Locale.ENGLISH, Locale.GERMAN), otherApp.getAvailableLocales(variant));
        assertEquals("title_de", otherApp.deriveLocalisedPropertyName(variant, "title", Locale.GERMAN));
    }

    // Originals, containers and items that are not nodes: Magnolia's own support
    @Test
    void test_variantLocale_onOtherItems() throws Exception {
        Node variant = variant("variant_de");
        Node container = variant.getParent();
        Node page = container.getParent();
        when(delegate.getAvailableLocales(any())).thenReturn(List.of(Locale.ENGLISH, Locale.GERMAN));
        when(delegate.deriveLocalisedPropertyName(page, "title", Locale.GERMAN)).thenReturn("title_de");
        when(delegate.getDefaultLocale(page)).thenReturn(Locale.ENGLISH);
        when(delegate.isDefaultLocale(Locale.ENGLISH, page)).thenReturn(true);

        assertEquals(List.of(Locale.ENGLISH, Locale.GERMAN), support.getAvailableLocales(page));
        assertEquals(List.of(Locale.ENGLISH, Locale.GERMAN), support.getAvailableLocales(container));
        assertEquals(List.of(Locale.ENGLISH, Locale.GERMAN), support.getAvailableLocales("not a node"));
        assertEquals("title_de", support.deriveLocalisedPropertyName(page, "title", Locale.GERMAN));
        assertEquals(Locale.ENGLISH, support.getDefaultLocale(page));
        assertTrue(support.isDefaultLocale(Locale.ENGLISH, page));
    }

    private static Node variant(String name) throws Exception {
        Node page = new MockNode().addNode("about", "mgnl:page");
        Node variant = page.addNode("localeVariants", "lv:localeVariants").addNode(name, "mgnl:page");
        variant.addMixin(LocaleVariantStructure.VARIANT_MIXIN);
        return variant;
    }
}
