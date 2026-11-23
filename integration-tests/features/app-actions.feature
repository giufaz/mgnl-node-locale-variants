@ui
Feature: Where "Add language variant" shows in the Promos app (content-types app, enabled by the demo)

  Background:
    * def ui = call read('ui/admincentral.js')
    * ui.login()

  Scenario: on a promo, in the "add" group after Magnolia's own actions, which keep their order
    * ui.openBrowser('promos', '/promo-plain')
    * ui.screenshot()
    * match ui.actions() == ['Add item (disabled)', 'Mark for deletion', 'Add language variant', 'Edit item', 'Rename item', 'Duplicate item', 'Move item', 'Publish', 'Publish incl. subnodes', 'Unpublish (disabled)', 'Export', 'Show versions (disabled)', 'Restore version (disabled)']

  Scenario: not at the root (nothing selected)
    * ui.openBrowser('promos', '/')
    * ui.screenshot()
    * match ui.actions() == ['Add item', 'Add folder', 'Duplicate item (disabled)', 'Move item (disabled)', 'Import']
