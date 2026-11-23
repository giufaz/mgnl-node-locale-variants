@delivery
Feature: Delivery API filter serves the locale variant

  Background:
    * url author + '/.rest/delivery/promos'

  Scenario Outline: <case>
    * def query = '<lang>' ? { lang: '<lang>' } : {}
    Given path '<promo>'
    And params query
    When method get
    Then status 200
    * match response['@name'] == '<served>'

    Examples:
      | case                              | promo    | lang  | served        |
      | de variant                        | promo-de | de    | variant_de    |
      | de_CH falls back to de            | promo-de | de_CH | variant_de    |
      | de finds the de_CH variant        | promo-ch | de    | variant_de_CH |
      | default locale serves original    | promo-de | en    | promo-de      |
      | lang=all serves original          | promo-de | all   | promo-de      |
      | no language serves original       | promo-de |       | promo-de      |

  Scenario: Accept-Language header
    Given path 'promo-de'
    And header Accept-Language = 'de-DE,de;q=0.9'
    When method get
    Then status 200
    * match response['@name'] == 'variant_de'
    * match response.title == 'Hallo'

  Scenario: children (@nodes) are swapped
    # Full URL: "path" would encode the @
    Given url author + '/.rest/delivery/promos/@nodes'
    And param lang = 'de'
    When method get
    Then status 200
    * def names = karate.map(response, function(n){ return n['@name'] })
    * match names contains ['variant_de', 'variant_de_CH']
    * match names !contains 'promo-de'

  Scenario: query results are left untouched
    Given params { lang: 'de', limit: 100 }
    When method get
    Then status 200
    * def names = karate.map(response.results, function(n){ return n['@name'] })
    * match names contains 'promo-de'

  Scenario: page editor requests on author get the original
    Given path 'promo-de'
    And params { lang: 'de', mgnlPreview: 'false' }
    When method get
    Then status 200
    * match response['@name'] == 'promo-de'

  Scenario: the page editor parameter is ignored on public
    Given url publicInstance + '/.rest/delivery/promos'
    And path 'promo-de'
    And params { lang: 'de', mgnlPreview: 'false' }
    When method get
    Then status 200
    * match response['@name'] == 'variant_de'

  Scenario: a page variant gets its missing areas (pages endpoint)
    Given url author + '/.rest/delivery/pages'
    And path 'it-areas'
    And param lang = 'de'
    When method get
    Then status 200
    * match response['@name'] == 'variant_de'
    * match response.main == '#notnull'

  Scenario: children (@nodes) are swapped on the pages endpoint too
    # Full URL: "path" would encode the @
    Given url author + '/.rest/delivery/pages/it-parent/@nodes'
    And param lang = 'de'
    When method get
    Then status 200
    * def names = karate.map(response, function(n){ return n['@name'] })
    * match names == ['variant_de', 'child-b']
