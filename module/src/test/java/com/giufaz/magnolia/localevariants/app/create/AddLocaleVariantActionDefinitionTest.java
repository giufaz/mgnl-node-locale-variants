package com.giufaz.magnolia.localevariants.app.create;

import static org.junit.jupiter.api.Assertions.*;

import info.magnolia.ui.dialog.actions.OpenDialogAction;

import org.junit.jupiter.api.Test;

class AddLocaleVariantActionDefinitionTest {

    // Everything that never changes is in the class; the decorated app adds only the availability
    @Test
    void test_defaults() {
        AddLocaleVariantActionDefinition definition = new AddLocaleVariantActionDefinition();
        assertEquals("node-locale-variants:addLocaleVariants", definition.getDialogId());
        assertFalse(definition.isPopulate(), "the dialog's field is not a property of the item");
        assertEquals(AddLocaleVariantActionDefinition.LABEL, definition.getLabel());
        assertEquals("icon-add-item", definition.getIcon());
        assertEquals(OpenDialogAction.class, definition.getImplementationClass(), "Magnolia's action opens the dialog");
    }
}
