package com.giufaz.magnolia.localevariants.app.create;

import info.magnolia.ui.api.action.ConfiguredActionDefinition;

/** "Create" action of the "Add language variants" dialog. */
public class CreateLocaleVariantsActionDefinition extends ConfiguredActionDefinition {

    public CreateLocaleVariantsActionDefinition() {
        setImplementationClass(CreateLocaleVariantsAction.class);
    }
}
