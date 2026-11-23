@ui @forms
Feature: A locale variant is edited in a form of one language, with its node name locked; originals keep their form

  # Detail sub-apps: the module opens a variant in the sub-app's twin definition (node name read-only, no i18n field).
  # Promos is a content-types app (React form); ctas is hand-written, its nodeNameProperty is "name".
  # Each scenario has its own item with a "de" variant (seed.groovy).

  Background:
    * def ui = call read('ui/admincentral.js')
    * ui.login()
    * def promos = author + '/.rest/nodes/v1/promos'
    * def ctas = author + '/.rest/nodes/v1/ctas'
    * def property = function(node, name){ var p = node.properties.find(function(x){ return x.name == name }); return p ? p.values : [] }

  # --- Detail sub-apps ---

  Scenario: a change saved in a variant's form goes to the variant, which keeps its node name; the original is untouched
    * ui.openBrowser('promos', '/promo-form/localeVariants/variant_de')
    * ui.clickAction('Edit item')
    # The field that renames the node on save can't be changed
    * match ui.formReadOnly('name') == true
    * match ui.location() == 'app:promos:detail;/promo-form/localeVariants/variant_de:edit'
    * match ui.formReadOnly('title') == false
    * ui.formType('title', 'Nur die Variante')
    * ui.screenshot()
    * ui.formSave()
    Given url promos + '/promo-form/localeVariants/variant_de'
    When method get
    Then status 200
    * match property(response, 'title') == ['Nur die Variante']
    Given url promos + '/promo-form'
    When method get
    Then status 200
    * match property(response, 'title') == ['Original']

  Scenario: a variant's form has one language, an original's form keeps its languages and its editable name
    * ui.openBrowser('promos', '/promo-form/localeVariants/variant_de')
    * ui.clickAction('Edit item')
    * match ui.formLanguages() == []
    * ui.openBrowser('promos', '/promo-form')
    * ui.clickAction('Edit item')
    * match ui.formReadOnly('name') == false
    * match ui.location() == 'app:promos:detail;/promo-form:edit'
    * match ui.formLanguages() == ['English (default)', 'German', 'Swiss High German']
    * ui.screenshot()

  Scenario: the labels of a variant's form are the ones of the original's form, translated
    * ui.openBrowser('ctas', '/cta-link')
    * ui.clickAction('Edit link cta')
    * def originalLabels = ui.formLabels()
    # A label found by its generated i18n key
    * match originalLabels contains 'Tracking id (translated)'
    * ui.openBrowser('ctas', '/cta-link/localeVariants/variant_de')
    * ui.clickAction('Edit link cta')
    * ui.screenshot()
    * match ui.formLabels() == originalLabels

  # --- Dialogs (openDialogAction): the banner CTA has no detail sub-app ---

  # PLANNED, with the dialog piece (seen red at runtime 2026-11-21, on the banner CTA of the demo):
  # saving a variant's dialog renames the variant: the dialog's "name" field holds the value copied from the
  # original, and Magnolia renames the node to it (nodeNameProperty), so variant_de becomes cta-banner and is no
  # longer a variant (the label then goes to label_de).
  # To activate: remove the leading "# " of the scenario below.
  # Scenario: a variant's dialog has one language and saves to the variant, which keeps its node name
  #   * ui.openBrowser('ctas', '/cta-banner/localeVariants/variant_de')
  #   * ui.clickAction('Edit banner cta')
  #   # No language on the i18n field, the variant's own value
  #   * match ui.dialogText() !contains 'Label ('
  #   * match ui.dialogValues() == ['cta-banner', 'cta-banner (de)', '']
  #   * ui.dialogType(1, 'Nur die Variante')
  #   * ui.screenshot()
  #   * ui.clickDialogButton('Save changes')
  #   * ui.waitForDialogClosed()
  #   # Saved in the variant's "label" (not "label_de"), and the node is still the "de" variant
  #   Given url ctas + '/cta-banner/localeVariants/variant_de'
  #   When method get
  #   Then status 200
  #   * match property(response, 'label') == ['Nur die Variante']
  #   * match property(response, 'label_de') == []
  #   Given url ctas + '/cta-banner'
  #   When method get
  #   Then status 200
  #   * match property(response, 'label') == ['cta-banner']
  #   * match property(response, 'label_de') == ['cta-banner (de)']

  # Not a defect: after a variant's dialog the original's dialog opens in the variant's language.
  # Magnolia keeps one current language per sub-app, and opening a variant is like picking its language.
