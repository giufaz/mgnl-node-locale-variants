package com.giufaz.magnolia.localevariants.app.create;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import info.magnolia.context.MgnlContext;
import info.magnolia.i18nsystem.SimpleTranslator;
import info.magnolia.test.mock.MockContext;
import info.magnolia.ui.CloseHandler;
import info.magnolia.ui.ValueContext;
import info.magnolia.ui.api.action.ActionExecutionException;
import info.magnolia.ui.api.context.UiContext;
import info.magnolia.ui.editor.EditorView;
import info.magnolia.ui.vaadin.overlay.MessageStyleTypeEnum;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.giufaz.magnolia.localevariants.LocaleVariantCreator;
import com.giufaz.magnolia.localevariants.LocaleVariantStructure;

class CreateLocaleVariantsActionTest {

    private static final Locale DE = Locale.GERMAN;
    private static final Locale DE_CH = new Locale("de", "CH");
    private static final Locale FR = Locale.FRENCH;

    @SuppressWarnings("unchecked")
    private final ValueContext<Node> valueContext = mock(ValueContext.class);
    @SuppressWarnings("unchecked")
    private final EditorView<Node> form = mock(EditorView.class);
    private final CloseHandler closeHandler = mock(CloseHandler.class);
    private final LocaleVariantStructure variantStructure = mock(LocaleVariantStructure.class);
    private final LocaleVariantCreator creator = mock(LocaleVariantCreator.class);
    private final UiContext uiContext = mock(UiContext.class);
    private final SimpleTranslator i18n = mock(SimpleTranslator.class);
    private final CreateLocaleVariantsAction action = new CreateLocaleVariantsAction(
            new CreateLocaleVariantsActionDefinition(), valueContext, form, closeHandler, variantStructure, creator,
            uiContext, i18n);

    private final Node original = mock(Node.class);
    private final Session session = mock(Session.class);

    // The site supports de, de_CH and fr (default en); the original has a "de" variant already
    @BeforeEach
    void setUp() throws RepositoryException {
        MockContext context = new MockContext();
        context.setLocale(Locale.ENGLISH);
        MgnlContext.setInstance(context);
        when(valueContext.getSingle()).thenReturn(Optional.of(original));
        when(original.getSession()).thenReturn(session);
        when(variantStructure.getSupportedVariantLocales(original)).thenReturn(List.of(DE, DE_CH, FR));
        when(variantStructure.hasVariant(original, DE)).thenReturn(true);
        when(creator.create(eq(original), any())).thenAnswer(invocation -> mock(Node.class));
        when(i18n.translate(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(0)
                + (invocation.getArguments().length > 1 ? " " + invocation.getArgument(1) : ""));
    }

    @AfterEach
    void tearDown() {
        MgnlContext.setInstance(null);
    }

    // Only the checked locales without a variant, in the site's order, saved once
    @Test
    void test_execute_createsTheCheckedLocales() throws Exception {
        checked(Locale.ENGLISH, DE, FR, DE_CH); // en (default) and de (existing) are locked in the field

        action.execute();

        InOrder order = inOrder(creator, session, closeHandler, valueContext);
        order.verify(creator).create(original, DE_CH);
        order.verify(creator).create(original, FR);
        order.verify(session).save();
        order.verify(closeHandler).close();
        order.verify(valueContext).set(any(Node.class));
        verify(creator, never()).create(original, DE);
        verify(creator, never()).create(original, Locale.ENGLISH);
        verify(uiContext).openNotification(eq(MessageStyleTypeEnum.INFO), eq(true),
                eq("node-locale-variants.dialog.created German (Switzerland), French"));
    }

    @Test
    void test_execute_nothingToCreate() throws Exception {
        checked(Locale.ENGLISH, DE); // only the locked ones

        action.execute();

        verifyNoInteractions(creator, closeHandler);
        verify(session, never()).save();
        verify(uiContext).openNotification(MessageStyleTypeEnum.WARNING, true,
                "node-locale-variants.dialog.nothingChecked");
    }

    @Test
    void test_execute_emptyField() throws Exception {
        when(form.getPropertyValue(CreateLocaleVariantsAction.FIELD)).thenReturn(Optional.empty());

        action.execute();

        verifyNoInteractions(creator);
        verify(uiContext).openNotification(eq(MessageStyleTypeEnum.WARNING), eq(true), anyString());
    }

    // All or none: on an error nothing is saved and the unsaved changes are dropped
    @Test
    void test_execute_withError() throws Exception {
        checked(DE_CH, FR);
        when(creator.create(original, FR)).thenThrow(new IllegalArgumentException("can't have variants"));

        assertThrows(ActionExecutionException.class, action::execute);

        verify(session, never()).save();
        verify(session).refresh(false);
        verifyNoInteractions(closeHandler);
    }

    @Test
    void test_execute_noItem() {
        when(valueContext.getSingle()).thenReturn(Optional.empty());

        assertThrows(ActionExecutionException.class, action::execute);
        verifyNoInteractions(creator);
    }

    private void checked(Locale... locales) {
        doReturn(Optional.of(Set.of(locales))).when(form).getPropertyValue(CreateLocaleVariantsAction.FIELD);
    }
}
