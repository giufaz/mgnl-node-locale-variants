package com.giufaz.magnolia.localevariants.app.browser;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import info.magnolia.i18nsystem.SimpleTranslator;
import info.magnolia.module.site.SiteManager;
import info.magnolia.test.mock.jcr.MockNode;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.giufaz.magnolia.localevariants.LocaleVariantStructure;
import com.giufaz.magnolia.localevariants.app.browser.LocalizedColumnDefinition.LocalizedValueProvider;
import com.giufaz.magnolia.localevariants.app.browser.LocalizedColumnDefinition.VariantLocalesTooltip;

class LocalizedColumnDefinitionTest {

    private final LocaleVariantStructure variantStructure = new LocaleVariantStructure(mock(SiteManager.class));
    private final SimpleTranslator i18n = mock(SimpleTranslator.class);
    private final VariantLocalesTooltip tooltip = new VariantLocalesTooltip(variantStructure, i18n);
    // Created in setUp: it translates its text when created
    private LocalizedValueProvider cell;

    private Node original;
    private Node container;
    private Node plain;

    // "summer-sale" with the variants de and de_CH, "winter-sale" without variants
    @BeforeEach
    void setUp() throws RepositoryException {
        Node root = new MockNode();
        original = root.addNode("summer-sale", "lvd:promo");
        container = original.addNode("localeVariants", "lv:localeVariants");
        container.addNode("variant_de", "lvd:promo").addMixin(LocaleVariantStructure.VARIANT_MIXIN);
        container.addNode("variant_de_CH", "lvd:promo").addMixin(LocaleVariantStructure.VARIANT_MIXIN);
        plain = root.addNode("winter-sale", "lvd:promo");
        when(i18n.translate("node-locale-variants.column.aria")).thenReturn("Has <language> variants");
        when(i18n.translate(eq("node-locale-variants.column.tooltip"), any()))
                .thenAnswer(call -> "Language variants: " + call.getArgument(1) + " <b>");
        cell = new LocalizedValueProvider(variantStructure, i18n);
    }

    @Test
    void test_definition() {
        LocalizedColumnDefinition definition = new LocalizedColumnDefinition();

        assertEquals("lvLocalized", definition.getName());
        assertEquals("node-locale-variants.column.label", definition.getLabel());
        assertFalse(definition.isSortable());
        // Magnolia's own check of a safe HTML column: a value provider that escapes
        definition.init();
    }

    @Test
    void test_cell_onAVariant() throws RepositoryException {
        assertEquals("<span class=\"lv-locale\">de</span>", cell.apply(container.getNode("variant_de")));
        assertEquals("<span class=\"lv-locale\">de_CH</span>", cell.apply(container.getNode("variant_de_CH")));
    }

    @Test
    void test_cell_onAnOriginalWithVariants() {
        String html = cell.apply(original);

        assertTrue(html.contains("icon-language-app"), html);
        assertTrue(html.contains("Has &lt;language&gt; variants"), "the text for screen readers, escaped: " + html);
    }

    @Test
    void test_cell_onOtherRows() throws RepositoryException {
        Property title = plain.setProperty("title", "Winter sale");

        assertEquals("", cell.apply(plain), "an item without variants");
        assertEquals("", cell.apply(container), "a container");
        assertEquals("", cell.apply(title), "a property");
    }

    @Test
    void test_cell_onARepositoryError() throws RepositoryException {
        Node broken = mock(Node.class);
        when(broken.isNodeType(any(String.class))).thenThrow(new RepositoryException("down"));

        assertEquals("", cell.apply(broken));
    }

    @Test
    void test_tooltip() throws RepositoryException {
        assertEquals("Language variants: de, de_CH &lt;b&gt;", tooltip.apply(original), "escaped: shown as HTML");
        assertNull(tooltip.apply(plain), "no variants");
        assertNull(tooltip.apply(container.getNode("variant_de")), "a variant");
        assertNull(tooltip.apply(plain.setProperty("title", "Winter sale")), "a property");
    }
}
