package com.giufaz.magnolia.localevariants.app.create;

import info.magnolia.context.MgnlContext;
import info.magnolia.i18nsystem.SimpleTranslator;
import info.magnolia.ui.CloseHandler;
import info.magnolia.ui.ValueContext;
import info.magnolia.ui.api.action.AbstractAction;
import info.magnolia.ui.api.action.ActionExecutionException;
import info.magnolia.ui.api.context.UiContext;
import info.magnolia.ui.editor.EditorView;
import info.magnolia.ui.vaadin.overlay.MessageStyleTypeEnum;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import com.giufaz.magnolia.localevariants.LocaleVariantCreator;
import com.giufaz.magnolia.localevariants.LocaleVariantStructure;

import jakarta.inject.Inject;

/**
 * Creates the variants of the checked locales on the selected item, in one
 * save (all or none), then selects the first one in the browser.
 *
 * <p>
 * The request is checked here, not only in the field: only the site's
 * supported locales without a variant are created.
 */
public class CreateLocaleVariantsAction extends AbstractAction<CreateLocaleVariantsActionDefinition> {

    static final String FIELD = "locales";

    private final ValueContext<Node> valueContext;
    private final EditorView<Node> form;
    private final CloseHandler closeHandler;
    private final LocaleVariantStructure variantStructure;
    private final LocaleVariantCreator creator;
    private final UiContext uiContext;
    private final SimpleTranslator i18n;

    @Inject
    public CreateLocaleVariantsAction(CreateLocaleVariantsActionDefinition definition, ValueContext<Node> valueContext,
            EditorView<Node> form, CloseHandler closeHandler, LocaleVariantStructure variantStructure,
            LocaleVariantCreator creator, UiContext uiContext, SimpleTranslator i18n) {
        super(definition);
        this.valueContext = valueContext;
        this.form = form;
        this.closeHandler = closeHandler;
        this.variantStructure = variantStructure;
        this.creator = creator;
        this.uiContext = uiContext;
        this.i18n = i18n;
    }

    @Override
    public void execute() throws ActionExecutionException {
        Node original = valueContext.getSingle().orElseThrow(() -> new ActionExecutionException("No item selected"));
        Set<Locale> checked = form.<Set<Locale>>getPropertyValue(FIELD).orElse(Set.of());
        try {
            List<Locale> requested = new ArrayList<>();
            // In the site's order; the locked checkboxes (default locale, existing
            // variants) are left out
            for (Locale locale : variantStructure.getSupportedVariantLocales(original)) {
                if (checked.contains(locale) && !variantStructure.hasVariant(original, locale)) {
                    requested.add(locale);
                }
            }
            if (requested.isEmpty()) {
                uiContext.openNotification(MessageStyleTypeEnum.WARNING, true,
                        i18n.translate("node-locale-variants.dialog.nothingChecked"));
                return;
            }
            List<Node> created = new ArrayList<>();
            for (Locale locale : requested) {
                created.add(creator.create(original, locale));
            }
            original.getSession().save();
            closeHandler.close();
            valueContext.set(created.get(0));
            Locale userLocale = MgnlContext.getLocale();
            String names = requested.stream().map(locale -> locale.getDisplayName(userLocale))
                    .collect(Collectors.joining(", "));
            uiContext.openNotification(MessageStyleTypeEnum.INFO, true,
                    i18n.translate("node-locale-variants.dialog.created", names));
        } catch (RepositoryException | RuntimeException e) {
            refresh(original);
            throw new ActionExecutionException("Can't create the locale variants of " + original, e);
        }
    }

    // Drops the unsaved part of a failed creation
    private static void refresh(Node original) {
        try {
            original.getSession().refresh(false);
        } catch (RepositoryException ignored) {
            // the session is unusable anyway
        }
    }
}
