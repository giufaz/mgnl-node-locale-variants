@ignore
Feature: Seed test content on author and public (test-only Groovy endpoint)

  Scenario:
    * def script = karate.readAsString('seed.groovy')
    Given url publicInstance + '/.rest/lv-demo/groovy'
    And header Content-Type = 'text/plain'
    And request script
    When method post
    Then status 200
    Given url author + '/.rest/lv-demo/groovy'
    And header Content-Type = 'text/plain'
    And request script
    When method post
    Then status 200
    * json seed = response
