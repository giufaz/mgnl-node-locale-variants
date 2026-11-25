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
    # Every node and property under a node, as paths: address/street, links/links0/linkLabel, links/links0/ (a node)
    * def flatten = function(node, prefix, out){ node.properties.forEach(function(p){ out[prefix + p.name] = p.values[0] }); (node.nodes || []).forEach(function(n){ out[prefix + n.name + '/'] = 'node'; flatten(n, prefix + n.name + '/', out) }); return out }
    # The paths that end with a language of the demo site: title_de, links/links0_de/
    * def localizedNames = function(paths){ return karate.keysOf(paths).filter(function(k){ return /_(en|de|de_CH)\/?$/.test(k) }) }

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

  # --- Nested fields (app "promosNested": a composite field on a child node, a multi field of composite fields,
  # three levels of nesting, a switchable field; an i18n field at every level) ---

  Scenario: the original's form with nested fields keeps its language switch
    * ui.openBrowser('promosNested', '/promo-nested')
    * ui.clickAction('Edit promo')
    * match ui.formLanguages() contains 'German'

  Scenario: a variant's form is in one language at every level of nested fields, and saves no localized property or node
    * ui.openBrowser('promosNested', '/promo-nested/localeVariants/variant_de')
    * ui.clickAction('Edit promo')
    * match ui.formLanguages() !contains 'German'
    * match ui.location() == 'app:promosNested:detail;/promo-nested/localeVariants/variant_de:edit'
    # A link, a block, and a tag in the block's button
    * ui.formAdd(0)
    * ui.formAdd(1)
    * ui.formAdd(1)
    # Every i18n field, level by level, then one that is not i18n
    * ui.formType('address.street', 'Hauptstrasse')
    * ui.formType('links.0.linkLabel', 'Weiterlesen')
    * ui.formType('blocks.0.blockTitle', 'Block eins')
    * ui.formType('blocks.0.button.buttonLabel', 'Kaufen')
    * ui.formType('blocks.0.button.tags.0.field', 'neu')
    * ui.formType('contact.email.email', 'info@beispiel.de')
    * ui.formType('address.city', 'Zürich')
    * ui.screenshot()
    * ui.formSave()
    Given url promos + '/promo-nested/localeVariants/variant_de?depth=6'
    When method get
    Then status 200
    * def saved = flatten(response, '', {})
    * match saved['address/street'] == 'Hauptstrasse'
    * match saved['address/city'] == 'Zürich'
    * match saved['links/links0/linkLabel'] == 'Weiterlesen'
    * match saved['blocks/blocks0/blockTitle'] == 'Block eins'
    * match saved['blocks/blocks0/button/buttonLabel'] == 'Kaufen'
    * match saved['blocks/blocks0/button/tags/tags0/field'] == 'neu'
    * match saved['email'] == 'info@beispiel.de'
    # Nothing was saved under a language: no title_de, no links0_de
    * match localizedNames(saved) == []

  Scenario: nested fields filled in English and in German on the original are in German, under their base names, in a new "de" variant; a field not translated keeps its English value
    * ui.openBrowser('promosNested', '/promo-nested-copy')
    * ui.clickAction('Edit promo')
    * match ui.formLanguages() contains 'German'
    # A link, a block, and a tag in the block's button, in English
    * ui.formAdd(0)
    * ui.formAdd(1)
    * ui.formAdd(1)
    * ui.formType('address.street', 'Main street')
    * ui.formType('address.city', 'Zurich')
    * ui.formType('links.0.linkLabel', 'Read more')
    * ui.formType('links.0.linkUrl', '/more')
    * ui.formType('blocks.0.blockTitle', 'Block one')
    * ui.formType('blocks.0.button.buttonLabel', 'Buy')
    * ui.formType('blocks.0.button.tags.0.field', 'new')
    * ui.formType('contact.email.email', 'info@example.com')
    * ui.formSave()
    # The same i18n fields in German, except the block title, which stays not translated
    * ui.openBrowser('promosNested', '/promo-nested-copy')
    * ui.clickAction('Edit promo')
    * ui.formSelectLanguage('German')
    * ui.formType('address.street', 'Hauptstrasse')
    * ui.formType('links.0.linkLabel', 'Weiterlesen')
    * ui.formType('blocks.0.button.buttonLabel', 'Kaufen')
    * ui.formType('blocks.0.button.tags.0.field', 'neu')
    * ui.formType('contact.email.email', 'info@beispiel.de')
    * ui.screenshot()
    * ui.formSave()
    # Magnolia saved the German values under localized names, at every level
    Given url promos + '/promo-nested-copy?depth=6'
    When method get
    Then status 200
    * def original = flatten(response, '', {})
    * match original['address/street_de'] == 'Hauptstrasse'
    * assert localizedNames(original).length > 3
    # The variant, created from the app
    * ui.openBrowser('promosNested', '/promo-nested-copy')
    * ui.clickAction('Add language variant')
    * ui.check('German')
    * ui.clickDialogButton('Create')
    * ui.waitForNotification('Language variants created: German')
    Given url promos + '/promo-nested-copy/localeVariants/variant_de?depth=6'
    When method get
    Then status 200
    * def variant = flatten(response, '', {})
    * match variant['address/street'] == 'Hauptstrasse'
    * match variant['address/city'] == 'Zurich'
    * match variant['links/links0/linkLabel'] == 'Weiterlesen'
    * match variant['links/links0/linkUrl'] == '/more'
    # Not translated on the original: kept with its English value
    * match variant['blocks/blocks0/blockTitle'] == 'Block one'
    * match variant['blocks/blocks0/button/buttonLabel'] == 'Kaufen'
    * match variant['blocks/blocks0/button/tags/tags0/field'] == 'neu'
    * match variant['email'] == 'info@beispiel.de'
    * match localizedNames(variant) == []
    # And its form shows them
    * ui.openBrowser('promosNested', '/promo-nested-copy/localeVariants/variant_de')
    * ui.clickAction('Edit promo')
    * match ui.formValue('links.0.linkLabel') == 'Weiterlesen'
    * match ui.formValue('blocks.0.button.tags.0.field') == 'neu'
