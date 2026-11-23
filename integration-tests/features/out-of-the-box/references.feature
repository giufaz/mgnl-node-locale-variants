@ootb
Feature: Out of the box: references Magnolia expands stay as they are

  Background:
    * url author + '/.rest/delivery/promos'

  Scenario: without a language the reference is the original
    Given path 'promo-ref'
    When method get
    Then status 200
    * match response.related['@name'] == 'promo-de'
    * match response.related.title == 'Hello'

  Scenario: a reference to a node without variants keeps Magnolia's i18n
    Given path 'promo-ref-plain'
    And param lang = 'de'
    When method get
    Then status 200
    * match response.related['@name'] == 'promo-plain'
    * match response.related.title == 'Schlicht'

  Scenario: query results keep the original references (queries are out of scope as a whole)
    Given params { lang: 'de', limit: 100 }
    When method get
    Then status 200
    * def ref = karate.filter(response.results, function(n){ return n['@name'] == 'promo-ref' })[0]
    * match ref.related['@name'] == 'promo-de'
