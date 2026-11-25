@ui @actions
Feature: Magnolia's own actions work on locale variants and on variants containers (Promos, the plainest content-types app)

  # The guard feature checks which actions are offered; here they are run, to see that no other rule of Magnolia
  # gets in the way. Only stock actions of a content-types app, with the module's default action lists.
  # Each scenario has its own item with a "de" variant (seed.groovy), all unpublished at the start.

  Background:
    * def ui = call read('ui/admincentral.js')
    * ui.login()
    * def promos = author + '/.rest/nodes/v1/promos'
    * def publicPromos = publicInstance + '/.rest/nodes/v1/promos'
    # Publishing takes a moment: the checks on public that expect the node wait for it
    * configure retry = { count: 15, interval: 2000 }
    * def property = function(node, name){ var p = node.properties.find(function(x){ return x.name == name }); return p ? p.values : [] }

  # --- Publishing ---

  Scenario: "Publish incl. subnodes" on the original publishes its container and its variants
    * ui.openBrowser('promos', '/promo-publish')
    * ui.clickAction('Publish incl. subnodes')
    Given url publicPromos + '/promo-publish/localeVariants/variant_de?includeMetadata=true'
    And retry until responseStatus == 200
    When method get
    Then status 200
    * match property(response, 'jcr:mixinTypes') contains 'lv:localeVariant'
    * ui.openBrowser('promos', '/promo-publish/localeVariants/variant_de')
    * match ui.selectedRow() contains 'Published'

  Scenario: "Publish" on the original alone leaves the variants unpublished; container and variant are then published one by one, and unpublished
    * ui.openBrowser('promos', '/promo-publish-steps')
    * ui.clickAction('Publish')
    * karate.pause(5000)
    Given url publicPromos + '/promo-publish-steps'
    When method get
    Then status 200
    Given url publicPromos + '/promo-publish-steps/localeVariants'
    When method get
    Then status 404
    # Magnolia's own rule: nothing is published under an unpublished parent
    * ui.openBrowser('promos', '/promo-publish-steps/localeVariants/variant_de')
    * match ui.actions() contains 'Publish (disabled)'
    * ui.openBrowser('promos', '/promo-publish-steps/localeVariants')
    * ui.clickAction('Publish')
    * karate.pause(5000)
    Given url publicPromos + '/promo-publish-steps/localeVariants'
    When method get
    Then status 200
    Given url publicPromos + '/promo-publish-steps/localeVariants/variant_de'
    When method get
    Then status 404
    * ui.openBrowser('promos', '/promo-publish-steps/localeVariants/variant_de')
    * ui.clickAction('Publish')
    * karate.pause(5000)
    Given url publicPromos + '/promo-publish-steps/localeVariants/variant_de'
    When method get
    Then status 200
    * match ui.selectedRow() contains 'Published'
    # Unpublish the variant: the container and the original stay published
    * ui.clickAction('Unpublish')
    * karate.pause(5000)
    Given url publicPromos + '/promo-publish-steps/localeVariants/variant_de'
    When method get
    Then status 404
    Given url publicPromos + '/promo-publish-steps/localeVariants'
    When method get
    Then status 200
    # Unpublish the container: the original stays published
    * ui.openBrowser('promos', '/promo-publish-steps/localeVariants')
    * ui.clickAction('Unpublish')
    * karate.pause(5000)
    Given url publicPromos + '/promo-publish-steps/localeVariants'
    When method get
    Then status 404
    Given url publicPromos + '/promo-publish-steps'
    When method get
    Then status 200

  # --- Editing ---

  Scenario: "Edit item" on a variant opens the form with the variant's own values
    * ui.openBrowser('promos', '/promo-edit/localeVariants/variant_de')
    * ui.clickAction('Edit item')
    * match ui.formValue('title') == 'Original'
    * match ui.location() == 'app:promos:detail;/promo-edit/localeVariants/variant_de:edit'
    * ui.screenshot()

  # Saving a variant's form: app-forms.feature

  # --- Deleting ---

  Scenario: a variant is marked for deletion, restored, marked again and deleted permanently
    * ui.openBrowser('promos', '/promo-delete/localeVariants/variant_de')
    * ui.clickAction('Mark for deletion')
    * match ui.confirmationText() contains 'Mark this item for deletion?'
    * match ui.confirmationText() contains '/promo-delete/localeVariants/variant_de'
    * ui.confirm('Mark 1 item for deletion')
    * match ui.selectedRow() contains 'Marked for deletion'
    * ui.screenshot()
    * match ui.actions() == ['Add item (disabled)', 'Mark for deletion (disabled)', 'Edit item (disabled)', 'Rename item (disabled)', 'Delete permanently', 'Restore item', 'Unpublish (disabled)', 'Export (disabled)']
    Given url promos + '/promo-delete/localeVariants/variant_de?includeMetadata=true'
    When method get
    Then status 200
    * match property(response, 'jcr:mixinTypes') contains 'mgnl:deleted'
    # Restored: its content and its variant mixin are back
    * ui.clickAction('Restore item')
    * ui.waitForNotification('Item successfully restored')
    * karate.pause(3000)
    Given url promos + '/promo-delete/localeVariants/variant_de?includeMetadata=true'
    When method get
    Then status 200
    * match property(response, 'jcr:mixinTypes') contains 'lv:localeVariant'
    * match property(response, 'jcr:mixinTypes') !contains 'mgnl:deleted'
    * match property(response, 'title') == ['Original']
    # Marked again and deleted for good: the container and the original stay
    * ui.openBrowser('promos', '/promo-delete/localeVariants/variant_de')
    * ui.clickAction('Mark for deletion')
    * ui.confirm('Mark 1 item for deletion')
    * ui.clickAction('Delete permanently')
    * match ui.confirmationText() contains 'Permanently delete this item?'
    * ui.confirm('Permanently delete 1 item')
    Given url promos + '/promo-delete/localeVariants/variant_de'
    When method get
    Then status 404
    Given url promos + '/promo-delete/localeVariants'
    When method get
    Then status 200

  Scenario: a container is marked for deletion with its variants, and deleted permanently: the original stays
    * ui.openBrowser('promos', '/promo-delete-container/localeVariants')
    * ui.clickAction('Mark for deletion')
    * match ui.confirmationText() contains 'Mark these items for deletion?'
    * match ui.confirmationText() contains '1 Sub-Item'
    * ui.confirm('Mark 2 items for deletion')
    * match ui.selectedRow() contains 'Marked for deletion'
    * match ui.actions() == ['Publish (disabled)', 'Unpublish (disabled)', 'Export (disabled)', 'Mark for deletion (disabled)', 'Delete permanently', 'Restore item']
    Given url promos + '/promo-delete-container/localeVariants/variant_de?includeMetadata=true'
    When method get
    Then status 200
    * match property(response, 'jcr:mixinTypes') contains 'mgnl:deleted'
    * ui.clickAction('Delete permanently')
    * ui.confirm('Permanently delete 2 items')
    Given url promos + '/promo-delete-container/localeVariants'
    When method get
    Then status 404
    Given url promos + '/promo-delete-container'
    When method get
    Then status 200
    # The original can get variants again
    * ui.openBrowser('promos', '/promo-delete-container')
    * match ui.actions() contains 'Add language variant'

  # "Restore item" is Magnolia's recursive restore in the apps with locale variants (module config
  # defaults.recursiveRestore): Magnolia's own action restores one node, and leaves the nodes under it deleted.

  Scenario: a container marked for deletion is restored with its variants
    * ui.openBrowser('promos', '/promo-restore-container/localeVariants')
    * ui.clickAction('Mark for deletion')
    * ui.confirm('Mark 2 items for deletion')
    * ui.clickAction('Restore item')
    * ui.waitForNotification('Item successfully restored')
    * karate.pause(3000)
    * ui.screenshot()
    Given url promos + '/promo-restore-container/localeVariants?includeMetadata=true'
    When method get
    Then status 200
    * match property(response, 'jcr:mixinTypes') !contains 'mgnl:deleted'
    Given url promos + '/promo-restore-container/localeVariants/variant_de?includeMetadata=true'
    When method get
    Then status 200
    * match property(response, 'jcr:mixinTypes') contains 'lv:localeVariant'
    * match property(response, 'jcr:mixinTypes') !contains 'mgnl:deleted'
    * match property(response, 'title') == ['Original']

  Scenario: an original marked for deletion with its container and variants is restored with them
    * ui.openBrowser('promos', '/promo-restore-original')
    * ui.clickAction('Mark for deletion')
    * match ui.confirmationText() contains '2 Sub'
    * ui.confirm('Mark 3 items for deletion')
    Given url promos + '/promo-restore-original/localeVariants/variant_de?includeMetadata=true'
    When method get
    Then status 200
    * match property(response, 'jcr:mixinTypes') contains 'mgnl:deleted'
    * ui.clickAction('Restore item')
    * ui.waitForNotification('Item successfully restored')
    * karate.pause(3000)
    * ui.screenshot()
    Given url promos + '/promo-restore-original?includeMetadata=true'
    When method get
    Then status 200
    * match property(response, 'jcr:mixinTypes') !contains 'mgnl:deleted'
    * match property(response, 'title') == ['Original']
    Given url promos + '/promo-restore-original/localeVariants?includeMetadata=true'
    When method get
    Then status 200
    * match property(response, 'jcr:mixinTypes') !contains 'mgnl:deleted'
    Given url promos + '/promo-restore-original/localeVariants/variant_de?includeMetadata=true'
    When method get
    Then status 200
    * match property(response, 'jcr:mixinTypes') contains 'lv:localeVariant'
    * match property(response, 'jcr:mixinTypes') !contains 'mgnl:deleted'
    * match property(response, 'title') == ['Original']

  Scenario: a published variant marked for deletion stays on public until it is deleted permanently; its container stays published
    * ui.openBrowser('promos', '/promo-delete-published')
    * ui.clickAction('Publish incl. subnodes')
    Given url publicPromos + '/promo-delete-published/localeVariants/variant_de'
    And retry until responseStatus == 200
    When method get
    Then status 200
    * ui.openBrowser('promos', '/promo-delete-published/localeVariants/variant_de')
    * ui.clickAction('Mark for deletion')
    * match ui.confirmationText() contains 'Published'
    * ui.confirm('Mark 1 item for deletion')
    * match ui.selectedRow() contains 'Marked for deletion'
    Given url publicPromos + '/promo-delete-published/localeVariants/variant_de'
    When method get
    Then status 200
    * ui.clickAction('Delete permanently')
    * ui.confirm('Permanently delete 1 item')
    Given url promos + '/promo-delete-published/localeVariants/variant_de'
    When method get
    Then status 404
    Given url publicPromos + '/promo-delete-published/localeVariants/variant_de'
    When method get
    Then status 404
    Given url publicPromos + '/promo-delete-published/localeVariants'
    When method get
    Then status 200

  # --- Export and import ---

  Scenario: "Export" on a variant and on a container downloads their YAML
    * ui.catchDownloads()
    * ui.openBrowser('promos', '/promo-export/localeVariants/variant_de')
    * ui.clickAction('Export')
    * match ui.downloads() == ['promos.promo-export.localeVariants.variant_de.yaml']
    * def variantYaml = ui.downloadedText('promos.promo-export.localeVariants.variant_de.yaml')
    * match variantYaml contains "'jcr:mixinTypes': ['lv:localeVariant']"
    * match variantYaml contains "'title': 'Original'"
    * ui.openBrowser('promos', '/promo-export/localeVariants')
    * ui.clickAction('Export')
    * match ui.downloads() contains 'promos.promo-export.localeVariants.yaml'
    * def containerYaml = ui.downloadedText('promos.promo-export.localeVariants.yaml')
    * match containerYaml contains "'jcr:primaryType': 'lv:localeVariants'"
    * match containerYaml contains "'variant_de':"

  # PLANNED (not built yet: the import dialog needs a file upload helper). "Import" is offered at the root and on
  # folders only, never on variants and containers (guard feature).
  # Scenario: an original exported with its variants and imported again comes back with its container and variants
  # Scenario: a variant's YAML imported next to another original is not a variant of it (what should happen: to decide)

  # --- Versions ---

  Scenario: a published variant has versions: "Show versions" and "Restore version" open their dialogs
    * ui.openBrowser('promos', '/promo-versions')
    * ui.clickAction('Publish incl. subnodes')
    Given url publicPromos + '/promo-versions/localeVariants/variant_de'
    And retry until responseStatus == 200
    When method get
    Then status 200
    * ui.openBrowser('promos', '/promo-versions/localeVariants/variant_de')
    * match ui.actions() contains ['Show versions', 'Restore version']
    * ui.clickAction('Show versions')
    * match ui.dialogText() contains 'Show version'
    * ui.clickDialogButton('Cancel')
    * ui.waitForDialogClosed()
    * ui.clickAction('Restore version')
    * match ui.dialogText() contains 'Restore version'
    * ui.screenshot()

  Scenario: "Restore version" brings back the variant's earlier content, and it is still the "de" variant
    # First version: the variant as seeded, published
    Given url promos + '/promo-version-restore/localeVariants/variant_de'
    When method get
    Then status 200
    * def seededTitle = property(response, 'title')
    * ui.openBrowser('promos', '/promo-version-restore')
    * ui.clickAction('Publish incl. subnodes')
    Given url publicPromos + '/promo-version-restore/localeVariants/variant_de'
    And retry until responseStatus == 200
    When method get
    Then status 200
    # Second version: the title changed in the form, published again
    * ui.openBrowser('promos', '/promo-version-restore/localeVariants/variant_de')
    * ui.clickAction('Edit item')
    * ui.formType('title', 'Changed')
    * ui.formSave()
    * ui.openBrowser('promos', '/promo-version-restore/localeVariants/variant_de')
    * ui.clickAction('Publish')
    Given url publicPromos + '/promo-version-restore/localeVariants/variant_de'
    And retry until responseStatus == 200 && property(response, 'title')[0] == 'Changed'
    When method get
    Then status 200
    # Restored to the first version
    * ui.openBrowser('promos', '/promo-version-restore/localeVariants/variant_de')
    * ui.clickAction('Restore version')
    * match ui.dialogText() contains 'Restore version'
    * ui.dialogSelect(0, '1.0')
    * ui.screenshot()
    * ui.clickDialogButton('Restore')
    * ui.waitForDialogClosed()
    Given url promos + '/promo-version-restore/localeVariants/variant_de?includeMetadata=true'
    And retry until property(response, 'title')[0] != 'Changed'
    When method get
    Then status 200
    * match property(response, 'title') == seededTitle
    * match response.name == 'variant_de'
    * match property(response, 'jcr:mixinTypes') contains 'lv:localeVariant'
    # The original and the container are untouched, and the variant is still offered as one in the app
    * ui.openBrowser('promos', '/promo-version-restore/localeVariants/variant_de')
    * match ui.selectedCell('Localized') == 'de'

  # PLANNED: Scenario: "Show versions" opens the variant's earlier version read-only (with the forms piece)
