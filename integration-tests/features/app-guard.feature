@ui @guard
Feature: What editors can do on locale variants and on variants containers (the guard)

  # Copy, move, duplicate, rename, paste and "Add language variant" are never available on them; the other actions as
  # the module config allows (defaults, or the app's own lists). Items that are neither stay as the app defines them.

  Background:
    * def ui = call read('ui/admincentral.js')
    * ui.login()

  # --- Promos: a content-types app with the default action lists ---

  Scenario: an item with variants keeps all its actions
    * ui.openBrowser('promos', '/promo-de')
    * match ui.actions() == ['Add item (disabled)', 'Mark for deletion', 'Add language variant', 'Edit item', 'Rename item', 'Duplicate item', 'Move item', 'Publish', 'Publish incl. subnodes', 'Unpublish (disabled)', 'Export', 'Show versions (disabled)', 'Restore version (disabled)']

  Scenario: a variant shows its item's section, with rename, duplicate, move and "Add language variant" disabled
    # Its row shows the original's name: the tree column reads the "name" property, copied to the variant
    * ui.openBrowser('promos', '/promo-de/localeVariants/variant_de')
    * match ui.selectedRowDepth() == 2
    * ui.screenshot()
    # Publish is disabled by Magnolia itself: the original is not published yet
    * match ui.actions() == ['Add item (disabled)', 'Mark for deletion', 'Add language variant (disabled)', 'Edit item', 'Rename item (disabled)', 'Duplicate item (disabled)', 'Move item (disabled)', 'Publish (disabled)', 'Publish incl. subnodes (disabled)', 'Unpublish (disabled)', 'Export', 'Show versions (disabled)', 'Restore version (disabled)']

  Scenario: a variants container has its own section, with the allowed container actions in the order of the config
    * ui.openBrowser('promos', '/promo-de/localeVariants')
    * match ui.selectedRow() contains 'localeVariants'
    * ui.screenshot()
    # Publish is disabled by Magnolia itself: the original is not published yet
    * match ui.actions() == ['Publish (disabled)', 'Unpublish (disabled)', 'Export', 'Mark for deletion', 'Delete permanently (disabled)', 'Restore item (disabled)']

  Scenario: the container of a published item can be published
    # promo-status was published by the seed
    * ui.openBrowser('promos', '/promo-status/localeVariants')
    * match ui.actions() contains 'Publish'

  Scenario: double-click on a variant opens it in the detail sub-app (editItem is in the default variant actions)
    * ui.openBrowser('promos', '/promo-de/localeVariants/variant_de')
    * ui.doubleClickSelectedRow()
    * ui.screenshot()
    * match ui.location() == 'app:promos:detail;/promo-de/localeVariants/variant_de:edit'

  Scenario: double-click on a container opens nothing (editItem is not in the container actions)
    * ui.openBrowser('promos', '/promo-de/localeVariants')
    * ui.doubleClickSelectedRow()
    * match ui.location() == 'app:promos:browser;/promo-de/localeVariants:treeview:'

  # --- promosHandWritten: a hand-written app (sections as a list, groups as a map, rules without names) ---

  Scenario: hand-written app, on a variant: its own actions, the guardrail ones disabled
    * ui.openBrowser('promosHandWritten', '/promo-de/localeVariants/variant_de')
    * match ui.actions() == ['Rename promo (disabled)', 'Add language variant (disabled)', 'Export promo']

  Scenario: hand-written app, on a container: only the allowed action the app has
    * ui.openBrowser('promosHandWritten', '/promo-de/localeVariants')
    * match ui.actions() == ['Export promo']

  # --- ctas: a hand-written multi-type app with its own action lists (decorations/node-locale-variants/ctas.config.yaml) ---

  Scenario: multi-type app, on an item of an enabled type: all its actions, plus "Add language variant"
    * ui.openBrowser('ctas', '/cta-social')
    * match ui.actions() == ['Mark for deletion', 'Add language variant', 'Edit social cta', 'Rename item', 'Duplicate item', 'Move item', 'Publish', 'Unpublish (disabled)', 'Export', 'Show versions (disabled)']

  Scenario: multi-type app, on a variant: the app's own edit action is allowed (variantActions: [editSocial, editLink])
    * ui.openBrowser('ctas', '/cta-social/localeVariants/variant_de')
    * ui.screenshot()
    * match ui.actions() == ['Mark for deletion', 'Add language variant (disabled)', 'Edit social cta', 'Rename item (disabled)', 'Duplicate item (disabled)', 'Move item (disabled)', 'Publish (disabled)', 'Unpublish (disabled)', 'Export', 'Show versions (disabled)']

  Scenario: multi-type app, double-click on a link variant opens the link form (the app's default action, editLink)
    * ui.openBrowser('ctas', '/cta-link/localeVariants/variant_de')
    * ui.doubleClickSelectedRow()
    * match ui.location() == 'app:ctas:linkDetail;/cta-link/localeVariants/variant_de:edit'

  Scenario: multi-type app, on a container: no action at all (containerActions: [], replacing the defaults)
    * ui.openBrowser('ctas', '/cta-social/localeVariants')
    * match ui.selectedRow() contains 'localeVariants'
    * ui.screenshot()
    * match ui.actions() == []
