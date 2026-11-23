@ui @browser
Feature: The browser of an app with locale variants shows what is localized: the "Localized" column and filter

  # The module adds the column after the first one of every view, and the filter after the app's own filters.
  # promo-de has a "de" variant, promo-ch a "de_CH" one, promo-plain none (seed.groovy).

  Background:
    * def ui = call read('ui/admincentral.js')
    * ui.login()

  # --- Column ---

  Scenario: an original with variants shows the icon, with the variants' languages as tooltip
    * ui.openBrowser('promos', '/promo-de')
    * match ui.selectedCellIcon('Localized') == 'icon-language-app'
    # The icon's meaning, for screen readers
    * match ui.selectedCell('Localized') == 'Has language variants'
    * match ui.selectedCellTooltip('Localized') == 'Language variants: de'
    * ui.openBrowser('promos', '/promo-ch')
    * match ui.selectedCellTooltip('Localized') == 'Language variants: de_CH'
    * ui.screenshot()

  Scenario: a variant shows its language (its row has the same name as its original)
    * ui.openBrowser('promos', '/promo-de/localeVariants/variant_de')
    * match ui.selectedCell('Localized') == 'de'
    * match ui.selectedCellIcon('Localized') == ''
    * match ui.selectedCellTooltip('Localized') == ''
    * ui.openBrowser('promos', '/promo-ch/localeVariants/variant_de_CH')
    * match ui.selectedCell('Localized') == 'de_CH'
    * ui.screenshot()

  Scenario: an item without variants, a folder and a variants container show nothing
    * ui.openBrowser('promos', '/promo-plain')
    * match ui.selectedCell('Localized') == ''
    * match ui.selectedCellIcon('Localized') == ''
    * match ui.selectedCellTooltip('Localized') == ''
    * ui.openBrowser('promos', '/promo-folder')
    * match ui.selectedCell('Localized') == ''
    * ui.openBrowser('promos', '/promo-de/localeVariants')
    * match ui.selectedCell('Localized') == ''
    * match ui.selectedCellIcon('Localized') == ''

  Scenario: the column is in an app written by hand too
    * ui.openBrowser('ctas', '/cta-link')
    * match ui.selectedCellIcon('Localized') == 'icon-language-app'
    * ui.openBrowser('ctas', '/cta-link/localeVariants/variant_de')
    * match ui.selectedCell('Localized') == 'de'

  # --- Filter ---

  Scenario: the filter lists the originals that have variants, and only them
    * ui.openBrowser('promos', '/promo-de')
    * ui.addFilter('Localized', 'Has language variants')
    * match ui.filters() == ['Localized: Has language variants']
    * def filtered = ui.rowNames()
    * ui.screenshot()
    * match filtered contains ['promo-de', 'promo-ch']
    # Not the items without variants, nor the containers and the variants themselves
    * match filtered !contains 'promo-plain'
    * match filtered !contains 'promo-folder'
    * match filtered !contains 'localeVariants'
    * match karate.distinct(filtered) == filtered
    # Without the filter every item is back
    * ui.removeFilters()
    * match ui.filters() == []
    * match ui.rowNames() contains ['promo-de', 'promo-plain', 'promo-folder']
