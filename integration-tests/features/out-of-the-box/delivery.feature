@ootb
Feature: Out of the box: Magnolia's own Delivery API behaviour stays as it is

  Nodes without variants and requests out of the module's scope must get exactly what Magnolia serves.

  Background:
    * url author + '/.rest/delivery/promos'

  Scenario: a node without variants keeps Magnolia's i18n (title_de for de)
    Given path 'promo-plain'
    And param lang = 'de'
    When method get
    Then status 200
    * match response['@name'] == 'promo-plain'
    * match response.title == 'Schlicht'

  Scenario: without a language Magnolia serves the base values
    Given path 'promo-plain'
    When method get
    Then status 200
    * match response.title == 'Plain'

  Scenario: an original with variants, out of scope (no language), is served with Magnolia's base values
    Given path 'promo-de'
    When method get
    Then status 200
    * match response['@name'] == 'promo-de'
    * match response.title == 'Hello'

  Scenario: children of a node without variants are the same with and without a language
    Given url author + '/.rest/delivery/pages/it-plain/@nodes'
    When method get
    Then status 200
    * def plainNames = karate.map(response, function(n){ return n['@name'] })
    Given url author + '/.rest/delivery/pages/it-plain/@nodes'
    And param lang = 'de'
    When method get
    Then status 200
    * def deNames = karate.map(response, function(n){ return n['@name'] })
    * match plainNames == ['c1', 'c2']
    * match deNames == plainNames

  Scenario: query results are the same with and without a language
    Given param limit = 100
    When method get
    Then status 200
    * def plainNames = karate.map(response.results, function(n){ return n['@name'] })
    Given params { limit: 100, lang: 'de' }
    When method get
    Then status 200
    * def deNames = karate.map(response.results, function(n){ return n['@name'] })
    * match deNames contains only plainNames

  Scenario: the pages endpoint still generates the areas of a page without variants
    Given url author + '/.rest/delivery/pages'
    And path 'it-plain'
    And param lang = 'de'
    When method get
    Then status 200
    * match response['@name'] == 'it-plain'
    * match response.main == '#notnull'

  Scenario: an original page with variants, out of scope (no language), is served by the pages endpoint with its areas
    Given url author + '/.rest/delivery/pages'
    And path 'it-page'
    When method get
    Then status 200
    * match response['@name'] == 'it-page'
    * match response.title == 'Hello'
    * match response.main == '#notnull'
