@ootb
Feature: Out of the box: the Nodes REST API (not Delivery) is never filtered

  Scenario: an original read through the Nodes API is the original, with its variants container as a child
    Given url author + '/.rest/nodes/v1/website'
    And path 'it-page'
    And params { depth: 1, lang: 'de' }
    When method get
    Then status 200
    * match response.name == 'it-page'
    * def children = karate.map(response.nodes, function(n){ return n.name })
    * match children contains 'localeVariants'
