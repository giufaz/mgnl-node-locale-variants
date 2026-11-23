@ui @config
Feature: A change of the module config applies to the running apps, without a restart

  # The config is changed as a project would: a decoration of node-locale-variants/config.yaml, here in a light module.
  # Magnolia restarts the module, which decorates the changed apps again (AppActivation).

  Background:
    * configure retry = { count: 20, interval: 2000 }
    # Not named "script": the browser driver defines its own "script" once it starts
    * def configScript = karate.readAsString('app-config-change.groovy')
    * configure afterScenario = function(){ karate.call('app-config-restore.feature') }

  Scenario: new action lists and a disabled app apply in a few seconds, and the demo's config comes back when the change is removed
    # Before: the demo's config
    Given url author + '/.rest/lv-demo/groovy'
    And header Content-Type = 'text/plain'
    And request configScript.replace('__ACTION__', 'read')
    When method post
    Then status 200
    * match response.promosContainerButtons == 'activate,deactivate,export,confirmMarkDeletion,deletePermanently,restorePreviousVersion'
    * match response.ctasDecorated == true
    * match response.variantDialogs == true

    # The change: promos allows only "export" on containers, ctas is switched off
    Given url author + '/.rest/lv-demo/groovy'
    And header Content-Type = 'text/plain'
    And request configScript.replace('__ACTION__', 'write')
    When method post
    Then status 200
    Given url author + '/.rest/lv-demo/groovy'
    And header Content-Type = 'text/plain'
    And request configScript.replace('__ACTION__', 'read')
    And retry until response.promosContainerActions == 'export' && response.ctasApplied == false
    When method post
    Then status 200
    # The apps are decorated again: the container section has one button, ctas is the app's own definition
    * match response.promosContainerButtons == 'export'
    * match response.promosDecorated == true
    * match response.ctasDecorated == false

    # What an editor who logs in now sees
    * def ui = call read('ui/admincentral.js')
    * ui.login()
    * ui.openBrowser('promos', '/promo-de/localeVariants')
    * ui.screenshot()
    * match ui.actions() == ['Export']
    # ctas without locale variants: no "Add language variant", and the container is not shown in the tree
    * ui.openBrowser('ctas', '/cta-social')
    * match ui.actions() == ['Mark for deletion', 'Edit social cta', 'Rename item', 'Duplicate item', 'Move item', 'Publish', 'Unpublish (disabled)', 'Export', 'Show versions (disabled)']
    * match each ui.treeRows() !contains 'localeVariants'

    # Removing the change brings the demo's config back (also done after the scenario, if it fails before)
    * def restored = call read('app-config-restore.feature')
    Given url author + '/.rest/lv-demo/groovy'
    And header Content-Type = 'text/plain'
    And request configScript.replace('__ACTION__', 'read')
    When method post
    Then status 200
    * match response.promosContainerButtons == 'activate,deactivate,export,confirmMarkDeletion,deletePermanently,restorePreviousVersion'
    * match response.ctasDecorated == true
    * match response.variantDialogs == true
