package com.giufaz.magnolia.localevariants.app.create;

import info.magnolia.ui.dialog.actions.OpenDialogActionDefinition;

/**
 * "Add language variant" browser action: opens the dialog {@value #DIALOG_ID}
 * on the selected item. Referenced by class (not {@code $type}) in the
 * decorated app, so no alias registration is needed.
 */
public class AddLocaleVariantActionDefinition extends OpenDialogActionDefinition {

    public static final String DIALOG_ID = "node-locale-variants:addLocaleVariants";
    public static final String LABEL = "node-locale-variants.actions.addLocaleVariant";

    public AddLocaleVariantActionDefinition() {
        setDialogId(DIALOG_ID);
        setPopulate(false); // the dialog's field is not a property of the item
        setLabel(LABEL);
        setIcon("icon-add-item");
    }
}
