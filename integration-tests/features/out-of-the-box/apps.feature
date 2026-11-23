@ui @ootb
Feature: Apps and item types not enabled in the module config stay as Magnolia ships them

  Background:
    * def ui = call read('../ui/admincentral.js')
    * ui.login()

  # promosStock: an app of the demo on the promos workspace, not in the module config
  Scenario: the app's own actions, in their order, and no "Add language variant" on an item with variants
    # promo-de has a "de" variant (seed)
    * ui.openBrowser('promosStock', '/promo-de')
    * match ui.selectedRow() contains 'promo-de'
    * ui.screenshot()
    * match ui.actions() == ['Rename promo', 'Export promo']

  Scenario: the variants container stays hidden in the tree, so it can't be selected
    * ui.openBrowser('promosStock', '/promo-de/localeVariants', { selects: false })
    * match ui.selectedRow() == ''
    * ui.screenshot()
    * match each ui.treeRows() !contains 'localeVariants'

  Scenario: in an app with locale variants, the items of a type left out of its nodeTypes keep the app's own actions
    # ctas enables lvd:ctaSocial and lvd:ctaLink, not lvd:ctaContact
    * ui.openBrowser('ctas', '/cta-contact')
    * match ui.actions() == ['Mark for deletion', 'Edit contact cta', 'Rename item', 'Duplicate item', 'Move item', 'Publish', 'Unpublish (disabled)', 'Export', 'Show versions (disabled)']
