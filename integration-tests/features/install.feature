@install
Feature: Module installation

  Background:
    * url author + '/.rest/nodes/v1'

  Scenario Outline: the module is installed with the project version on <instance>
    Given url <instance> + '/.rest/nodes/v1'
    And path 'config', 'modules', 'node-locale-variants'
    And param depth = 0
    When method get
    Then status 200
    * def version = karate.filter(response.properties, function(p){ return p.name == 'version' })[0].values[0]
    * match version == projectVersion

    Examples:
      | instance       |
      | author         |
      | publicInstance |

  Scenario: the lv node types are registered
    Given path 'website', ''
    And request { name: 'it-types', type: 'mgnl:page', path: '/it-types', properties: [] }
    When method put
    Then status 200
    Given path 'website', 'it-types'
    And request { name: 'localeVariants', type: 'lv:localeVariants', path: '/it-types/localeVariants', properties: [] }
    When method put
    Then status 200
    Given path 'website', 'it-types'
    And request { name: 'unknown', type: 'lv:doesNotExist', path: '/it-types/unknown', properties: [] }
    When method put
    Then assert responseStatus >= 400
    Given path 'website', 'it-types'
    When method delete
    Then status 200
