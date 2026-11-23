@references
Feature: References to an original are served as its locale variant (Delivery API)

  Background:
    * url author + '/.rest/delivery/promos'

  Scenario: a reference to an original with a variant expands the variant
    Given path 'promo-ref'
    And param lang = 'de'
    When method get
    Then status 200
    * match response.related['@name'] == 'variant_de'
    * match response.related.title == 'Hallo'

  Scenario: de_CH falls back to the de variant in references too
    Given path 'promo-ref'
    And param lang = 'de_CH'
    When method get
    Then status 200
    * match response.related['@name'] == 'variant_de'

  Scenario: a direct reference to a variant is served as is
    Given path 'promo-ref-variant'
    And param lang = 'de'
    When method get
    Then status 200
    * match response.related['@name'] == 'variant_de'

  Scenario: nested references (referenceDepth 2) are localized at every level
    Given url author + '/.rest/delivery/promosdeep'
    And path 'promo-n1'
    And param lang = 'de'
    When method get
    Then status 200
    * match response.related['@name'] == 'promo-n2'
    * match response.related.related['@name'] == 'variant_de'

  Scenario: a reference cycle between originals with variants stays bounded by referenceDepth
    Given url author + '/.rest/delivery/promosdeep'
    And path 'promo-x'
    And param lang = 'de'
    When method get
    Then status 200
    * match response['@name'] == 'variant_de'
    * match response.related['@name'] == 'variant_de'

  Scenario: references inside children (@nodes) are localized
    Given url author + '/.rest/delivery/promos/@nodes'
    And param lang = 'de'
    When method get
    Then status 200
    * def ref = karate.filter(response, function(n){ return n['@name'] == 'promo-ref' })[0]
    * match ref.related['@name'] == 'variant_de'

  Scenario: references are localized on public too
    Given url publicInstance + '/.rest/delivery/promos'
    And path 'promo-ref'
    And param lang = 'de'
    When method get
    Then status 200
    * match response.related['@name'] == 'variant_de'
