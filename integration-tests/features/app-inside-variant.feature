@ui @inside
Feature: A node created under a locale variant is in one language, like its variant

  # A variant can get child nodes of its own: a page under a page variant (app-pages.feature), or in a content app an
  # item under an item's variant, here a link CTA and a banner CTA under the "de" variant of cta-inside (seed.groovy).
  # Such a node is no variant itself: it keeps the actions of an ordinary item, but it is edited in one language.
  # Today the module's pieces don't agree on it: see the PLANNED scenarios below.

  Background:
    * def ui = call read('ui/admincentral.js')
    * ui.login()

  Scenario: an item under a variant keeps the actions of an ordinary item
    * ui.openBrowser('ctas', '/cta-inside/localeVariants/variant_de/link-inside')
    * match ui.selectedRowDepth() == 3
    * ui.screenshot()
    * match ui.actions() contains ['Edit link cta', 'Rename item', 'Duplicate item', 'Move item', 'Mark for deletion']
    * match ui.selectedCell('Localized') == ''

  Scenario: the dialog of an item under a variant has one language
    * ui.openBrowser('ctas', '/cta-inside/localeVariants/variant_de/banner-inside')
    * ui.clickAction('Edit banner cta')
    * ui.screenshot()
    # No language on the field; the name can be changed (it is an item of its own)
    * match ui.dialogText() !contains 'Label ('
    * match ui.dialogValues() == ['banner-inside', 'banner-inside', '']

  # PLANNED, with the fix "a node inside a variant" (both seen red at runtime 2026-11-23):
  # 1. The form of an item under a variant is the app's own form, with the language switch (English, German, Swiss
  #    High German): the twin form is used only when the item opened is itself a variant. Wanted: one language, and
  #    the name still editable (the twin of a variant locks it).
  # 2. "Add language variant" is available on an item under a variant, and opens its dialog: a variant inside a
  #    variant. Wanted: not available. The same on a page under a page variant (app-pages.feature).
  # To activate: remove the leading "# " of the two scenarios below.
  # Scenario: the form of an item under a variant has one language, and its name can be changed
  #   * ui.openBrowser('ctas', '/cta-inside/localeVariants/variant_de/link-inside')
  #   * ui.clickAction('Edit link cta')
  #   * ui.screenshot()
  #   * match ui.formLanguages() == []
  #   * match ui.formReadOnly('name') == false

  # Scenario: "Add language variant" is not available on an item under a variant
  #   * ui.openBrowser('ctas', '/cta-inside/localeVariants/variant_de/link-inside')
  #   * match ui.actions() contains 'Add language variant (disabled)'
