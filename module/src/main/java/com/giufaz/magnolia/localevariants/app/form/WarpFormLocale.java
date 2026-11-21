package com.giufaz.magnolia.localevariants.app.form;

import info.magnolia.objectfactory.ComponentProvider;
import info.magnolia.ui.api.view.View;
import info.magnolia.warp.engine.form.SelectedLocale;

import java.util.function.Supplier;

/**
 * The locale of the React (Warp) detail forms, which Magnolia remembers per
 * app. A variant's form has no i18n field, so a remembered non-default locale
 * would open it with every field read-only (WARPFORM-1330).
 *
 * <p>
 * In its own class because it references Warp classes, absent where the
 * forms are Vaadin only.
 */
final class WarpFormLocale {

    private WarpFormLocale() {
    }

    // Opens the view (the sub-app start) with no remembered locale: the form
    // reads it while it opens and falls back to the default one. Then puts the
    // editor's locale back for the next forms of the app
    static View withDefaultLocale(ComponentProvider componentProvider, Supplier<View> viewOpener) {
        SelectedLocale selected = componentProvider.getComponent(SelectedLocale.class);
        String previous = selected.getLocale();
        selected.setLocale(null);
        try {
            return viewOpener.get();
        } finally {
            selected.setLocale(previous);
        }
    }
}
