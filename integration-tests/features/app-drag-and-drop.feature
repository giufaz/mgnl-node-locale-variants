@guard
Feature: Drag and drop in the tree: variants and containers can't be dragged, nothing is dropped on or next to them

  # The tree's own drop constraint is wrapped by the module's; the rest is decided by Magnolia's, as before.
  # First the constraint asked directly, with real nodes (every case); then real drags in the tree (ui.drag), where a
  # refused drop shows Magnolia's own warning, the same as for a drop Magnolia itself refuses.

  Background:
    * def refused = 'The item has not been moved because of a drag-and-drop constraint.'
    # Puts the item of the last scenario back at the top level (also before: a failed run may have left it moved)
    * def putBack = "import info.magnolia.context.MgnlContext\ndef s = MgnlContext.getJCRSession('promos')\nif (s.nodeExists('/promo-folder/promo-drag')) { s.move('/promo-folder/promo-drag', '/promo-drag'); s.save() }\nnull"

  Scenario: the module's constraint, asked with real nodes (Magnolia's own constraint alone would allow them)
    Given url author + '/.rest/lv-demo/groovy'
    And header Content-Type = 'text/plain'
    And request karate.readAsString('app-drag-and-drop.groovy')
    When method post
    Then status 200
    * json drop = response
    # The promos tree: Magnolia's constraint, wrapped
    * match drop.promosConstraint == 'LocaleVariantDropConstraintDefinition'
    * match drop.promosDelegate == 'JcrDropConstraintDefinition'
    # Items that are neither variants nor containers: as Magnolia decides
    * match drop.itemIntoFolder == true
    * match drop.itemNextToItem == true
    * match drop.itemIntoItsOwnParent == false
    * match drop.sameAsMagnoliaOnOtherItems == true
    # Variants and containers can't be dragged
    * match drop.variantIntoFolder == false
    * match drop.containerIntoFolder == false
    * match drop.itemAndVariantIntoFolder == false
    # Nothing is dropped on or next to them
    * match drop.itemOnContainer == false
    * match drop.itemNextToContainer == false
    * match drop.itemOnVariant == false
    * match drop.itemNextToVariant == false
    # Without the module Magnolia would allow these
    * match drop.magnoliaAloneVariantIntoFolder == true
    * match drop.magnoliaAloneItemOnContainer == true
    # Trees without drag and drop stay without; apps not in the module config keep Magnolia's constraint
    * match drop.ctasConstraint == 'none'
    * match drop.promosHandWrittenConstraint == 'none'
    * match drop.notEnabledConstraint != 'LocaleVariantDropConstraintDefinition'

  @ui
  Scenario: a variant and a container dragged to a folder stay where they are
    * def ui = call read('ui/admincentral.js')
    * ui.login()
    * ui.openBrowser('promos', '/promo-de/localeVariants/variant_de')
    # The variant's row shows its original's name, two levels down
    * match ui.drag({ name: 'promo-de', depth: 2 }, { name: 'promo-folder' }) == true
    * match ui.notifications() == [ '#(refused)' ]
    * ui.screenshot()
    * match ui.drag({ name: 'localeVariants' }, { name: 'promo-folder' }) == true
    * match ui.notifications() contains refused
    Given url author + '/.rest/nodes/v1/promos/promo-de/localeVariants/variant_de'
    When method get
    Then status 200
    Given url author + '/.rest/nodes/v1/promos/promo-de/localeVariants'
    When method get
    Then status 200
    Given url author + '/.rest/nodes/v1/promos/promo-folder/variant_de'
    When method get
    Then status 404
    Given url author + '/.rest/nodes/v1/promos/promo-folder/localeVariants'
    When method get
    Then status 404

  @ui
  Scenario: an item dropped on a container or on a variant stays where it is
    * def ui = call read('ui/admincentral.js')
    * ui.login()
    * ui.openBrowser('promos', '/promo-de/localeVariants/variant_de')
    * match ui.drag({ name: 'promo-plain' }, { name: 'localeVariants' }) == true
    * match ui.notifications() == [ '#(refused)' ]
    * match ui.drag({ name: 'promo-plain' }, { name: 'promo-de', depth: 2 }) == true
    * match ui.notifications() contains refused
    Given url author + '/.rest/nodes/v1/promos/promo-plain'
    When method get
    Then status 200
    Given url author + '/.rest/nodes/v1/promos/promo-de/localeVariants/promo-plain'
    When method get
    Then status 404
    Given url author + '/.rest/nodes/v1/promos/promo-de/localeVariants/variant_de/promo-plain'
    When method get
    Then status 404

  # The positive control of the two scenarios above: the same gesture does move an item
  @ui
  Scenario: an item dragged to a folder still moves
    Given url author + '/.rest/lv-demo/groovy'
    And header Content-Type = 'text/plain'
    And request putBack
    When method post
    Then status 200
    * def ui = call read('ui/admincentral.js')
    * ui.login()
    * ui.openBrowser('promos', '/promo-drag')
    * match ui.drag({ name: 'promo-drag' }, { name: 'promo-folder' }) == true
    * match ui.notifications() == []
    * ui.screenshot()
    Given url author + '/.rest/nodes/v1/promos/promo-folder/promo-drag'
    When method get
    Then status 200
    Given url author + '/.rest/lv-demo/groovy'
    And header Content-Type = 'text/plain'
    And request putBack
    When method post
    Then status 200
