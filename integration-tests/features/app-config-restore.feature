@ignore
Feature: Remove the test decoration of the module config and wait for the demo's config to be applied again

  Scenario:
    * configure retry = { count: 20, interval: 2000 }
    * def configScript = karate.readAsString('app-config-change.groovy')
    Given url author + '/.rest/lv-demo/groovy'
    And header Content-Type = 'text/plain'
    And request configScript.replace('__ACTION__', 'remove')
    When method post
    Then status 200
    Given url author + '/.rest/lv-demo/groovy'
    And header Content-Type = 'text/plain'
    And request configScript.replace('__ACTION__', 'read')
    And retry until response.ctasApplied == true && response.promosContainerButtons.startsWith('activate,')
    When method post
    Then status 200
