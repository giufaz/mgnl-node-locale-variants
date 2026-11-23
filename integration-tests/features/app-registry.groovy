// Reads the decorated apps from Magnolia's app registry (ours, type-mapped by the module). Prints a JSON object.
import info.magnolia.objectfactory.Components
import info.magnolia.ui.api.app.registry.AppDescriptorRegistry

def registry = Components.getComponent(AppDescriptorRegistry)
def first = registry.getProvider("promos")
def second = registry.getProvider("promos")
def result = [
    registryClass: registry.getClass().getSimpleName(),
    // true = served from the registry's cache: Magnolia's raw view stays the same instance between lookups
    sameDecoratedProvider: first.is(second),
    decoratedByModule: first.getDecorators().any { it.toString().contains("node-locale-variants") },
    // Not enabled in the module config: Magnolia's own provider
    notEnabledDecorated: registry.getProvider("promosStock").getDecorators().any { it.toString().contains("node-locale-variants") }
]
def json = { v -> v instanceof String ? '"' + v + '"' : String.valueOf(v) }
out.print('{' + result.collect { k, v -> '"' + k + '":' + json(v) }.join(',') + '}')
null
