@apps
Feature: App registry (the module's AppDescriptorRegistry decorates the enabled apps)

  Background:
    Given url author + '/.rest/lv-demo/groovy'
    And header Content-Type = 'text/plain'
    And request karate.readAsString('app-registry.groovy')
    When method post
    Then status 200
    * json apps = response

  Scenario: the module's registry is the one in use
    * match apps.registryClass == 'LocaleVariantsAppDescriptorRegistry'

  Scenario: an enabled app is decorated once and then served from the cache, not decorated on every lookup
    * match apps.decoratedByModule == true
    * match apps.sameDecoratedProvider == true

  Scenario: an app not in the module config is Magnolia's own
    * match apps.notEnabledDecorated == false
