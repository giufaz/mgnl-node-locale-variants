package com.giufaz.magnolia.localevariants.app.create;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import info.magnolia.cms.i18n.I18nContentSupport;
import info.magnolia.context.MgnlContext;
import info.magnolia.i18nsystem.SimpleTranslator;
import info.magnolia.objectfactory.ComponentProvider;
import info.magnolia.test.mock.MockContext;
import info.magnolia.ui.ValueContext;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.giufaz.magnolia.localevariants.LocaleVariantStructure;
import com.vaadin.data.provider.ListDataProvider;
import com.vaadin.ui.CheckBoxGroup;

class LocaleVariantLocalesFieldFactoryTest {

    private static final Locale EN = Locale.ENGLISH;
    private static final Locale DE = Locale.GERMAN;
    private static final Locale DE_CH = new Locale("de", "CH");

    @SuppressWarnings("unchecked")
    private final ValueContext<Node> valueContext = mock(ValueContext.class);
    private final LocaleVariantStructure variantStructure = mock(LocaleVariantStructure.class);
    private final SimpleTranslator i18n = mock(SimpleTranslator.class);
    private final LocaleVariantLocalesFieldFactory factory = new LocaleVariantLocalesFieldFactory(
            new LocaleVariantLocalesFieldDefinition(), mock(ComponentProvider.class), valueContext, variantStructure,
            i18n);

    private final Node original = mock(Node.class);

    // Site en (default), de, de_CH; the original has a "de" variant
    @BeforeEach
    void setUp() throws RepositoryException {
        MockContext context = new MockContext();
        context.setLocale(EN);
        MgnlContext.setInstance(context);
        I18nContentSupport siteI18n = mock(I18nContentSupport.class);
        when(siteI18n.getDefaultLocale()).thenReturn(EN);
        when(valueContext.getSingle()).thenReturn(Optional.of(original));
        when(variantStructure.getSiteI18n(original)).thenReturn(siteI18n);
        when(variantStructure.getSupportedVariantLocales(original)).thenReturn(List.of(DE, DE_CH));
        when(variantStructure.hasVariant(original, DE)).thenReturn(true);
        when(i18n.translate(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        MgnlContext.setInstance(null);
    }

    @Test
    void test_createFieldComponent_checkboxes() {
        CheckBoxGroup<Locale> group = field();

        assertEquals(List.of(EN, DE, DE_CH), items(group), "default first, then the site's order");
        assertEquals(Set.of(EN, DE), group.getValue(), "default and existing variant checked");
        assertFalse(group.getItemEnabledProvider().test(EN), "default locked");
        assertFalse(group.getItemEnabledProvider().test(DE), "existing variant locked");
        assertTrue(group.getItemEnabledProvider().test(DE_CH));
        assertEquals("English — node-locale-variants.dialog.locales.default", group.getItemCaptionGenerator().apply(EN));
        assertEquals("German — node-locale-variants.dialog.locales.exists", group.getItemCaptionGenerator().apply(DE));
        assertEquals("German (Switzerland)", group.getItemCaptionGenerator().apply(DE_CH));
    }

    // Verified at runtime (CE 6.4.10, 2026-11-20): Magnolia's form resets the field
    // value after the factory, so without the listener the locked ones show unchecked
    @Test
    void test_createFieldComponent_lockedStayChecked() {
        CheckBoxGroup<Locale> group = field();

        group.setValue(Set.of());
        assertEquals(Set.of(EN, DE), group.getValue(), "reset by the form");

        group.setValue(Set.of(DE_CH));
        assertEquals(Set.of(EN, DE, DE_CH), group.getValue(), "a free locale checked");
    }

    // A variant that can't be read is not offered
    @Test
    void test_createFieldComponent_readError() throws RepositoryException {
        when(variantStructure.hasVariant(original, DE_CH)).thenThrow(new RepositoryException("boom"));

        assertFalse(field().getItemEnabledProvider().test(DE_CH));
    }

    @Test
    void test_createFieldComponent_noItem() {
        when(valueContext.getSingle()).thenReturn(Optional.empty());

        assertEquals(List.of(), items(field()));
    }

    @SuppressWarnings("unchecked")
    private CheckBoxGroup<Locale> field() {
        return (CheckBoxGroup<Locale>) factory.createFieldComponent();
    }

    @SuppressWarnings("unchecked")
    private static List<Locale> items(CheckBoxGroup<Locale> group) {
        return group.getDataProvider() instanceof ListDataProvider<?> list
                ? List.copyOf((java.util.Collection<Locale>) list.getItems()) : List.of();
    }
}
