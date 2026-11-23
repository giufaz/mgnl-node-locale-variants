@ui
Feature: Create locale variants in the AdminCentral (Promos app, enabled by the demo's module config)

  Background:
    * def ui = call read('ui/admincentral.js')
    * ui.login()

  Scenario: "Add language variant" creates the checked locale, selected in the tree under its container
    * ui.openBrowser('promos', '/promo-ui')
    * ui.clickAction('Add language variant')
    * match ui.dialogCheckboxes() == ['English — default (the original) [x] disabled', 'German [ ]', 'German (Switzerland) [ ]']
    * ui.screenshot()
    * ui.check('German')
    * ui.clickDialogButton('Create')
    * ui.waitForNotification('Language variants created: German')
    * match ui.dialogOpen() == false
    # The new variant is selected: promo-ui / localeVariants / variant_de
    * match ui.selectedRowDepth() == 2
    * ui.screenshot()
    * ui.openBrowser('promos', '/promo-ui/localeVariants')
    * match ui.selectedRow() contains 'localeVariants'
    # Saved as LocaleVariantCreator makes it (see creator.feature), by the logged-in user
    Given url author + '/.rest/nodes/v1/promos/promo-ui/localeVariants/variant_de'
    And param includeMetadata = true
    When method get
    Then status 200
    * match response.type == 'lvd:promo'
    * def props = {}
    * karate.forEach(response.properties, function(p){ props[p.name] = p.values })
    * match props.title == ['Oberfläche']
    * match props['mgnl:createdBy'] == ['superuser']

  Scenario: the default locale and the existing variants are checked and locked
    # promo-de has a "de" variant (seed). The field keeps them checked although the form resets its value
    * ui.openBrowser('promos', '/promo-de')
    * ui.clickAction('Add language variant')
    * match ui.dialogCheckboxes() == ['English — default (the original) [x] disabled', 'German — exists [x] disabled', 'German (Switzerland) [ ]']
    * ui.screenshot()
    * ui.clickDialogButton('Cancel')
    * match ui.dialogOpen() == false

  Scenario: "Create" with no new locale checked warns and creates nothing
    * ui.openBrowser('promos', '/promo-plain')
    * ui.clickAction('Add language variant')
    * ui.clickDialogButton('Create')
    * ui.waitForNotification('Check at least one language that has no variant yet.')
    * match ui.dialogOpen() == true
    * ui.clickDialogButton('Cancel')
    Given url author + '/.rest/nodes/v1/promos/promo-plain/localeVariants'
    When method get
    Then status 404

  Scenario: a hand-written app with list-form sections gets the action in its first group, its own actions kept
    # promosHandWritten: sections, groups and items as lists (- name:), no "addActions" group
    * ui.openBrowser('promosHandWritten', '/promo-plain')
    * match ui.actions() == ['Rename promo', 'Add language variant', 'Export promo']
