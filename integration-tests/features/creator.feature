@creator
Feature: Variant creation (LocaleVariantCreator, run by the seed)

  Background:
    * url author + '/.rest/nodes/v1/website'

  Scenario: a variant is a mono-lingual copy of the original
    Given path 'it-page', 'localeVariants', 'variant_de'
    And param includeMetadata = true
    When method get
    Then status 200
    * match response.type == 'mgnl:page'
    * def props = {}
    * karate.forEach(response.properties, function(p){ props[p.name] = p.values })
    * match props.title == ['Hallo']
    * def names = karate.map(response.properties, function(p){ return p.name })
    * match names !contains 'title_de'
    * match props['mgnl:template'] == ['node-locale-variants-demo:pages/basic']
    * match props['mgnl:createdBy'] == ['superuser']
    * match seed.variantMixins contains 'lv:localeVariant'

  Scenario: the container is created first among the original's children
    Given path 'it-page'
    And param depth = 1
    When method get
    Then status 200
    * match response.nodes[0].name == 'localeVariants'
    * match response.nodes[0].type == 'lv:localeVariants'

  Scenario Outline: the original turns Modified only with its first variant: <kind>
    * match seed['<kind>Before'] == 2
    * match seed['<kind>AfterFirstVariant'] == 1
    * match seed['<kind>AfterSecondVariant'] == 2

    Examples:
      | kind |
      | page |
      | item |

  Scenario Outline: the container gets created/modified metadata from Magnolia's session: <kind>
    * match seed['<kind>ContainerCreatedBy'] == 'superuser'
    * match seed['<kind>ContainerHasDates'] == true

    Examples:
      | kind |
      | page |
      | item |

  Scenario: a second variant touches the item container, not the page container (pages exempt in Magnolia's wrapper)
    * match seed.pageContainerTouchedBySecondVariant == false
    * match seed.itemContainerTouchedBySecondVariant == true
