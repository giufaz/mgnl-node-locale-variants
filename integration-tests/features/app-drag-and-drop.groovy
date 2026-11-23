// Asks the drop constraint of the decorated Promos tree about real nodes, as the tree does while dragging.
// Prints a JSON object.
import info.magnolia.context.MgnlContext
import info.magnolia.objectfactory.Components
import info.magnolia.ui.api.app.registry.AppDescriptorRegistry
import com.vaadin.shared.ui.grid.DropLocation

def registry = Components.getComponent(AppDescriptorRegistry)
def treeConstraint = { String app ->
    registry.getProvider(app).get().getSubApps().get("browser").getWorkbench().getContentViews().find { it.name == "tree" }.dropConstraint
}
def name = { definition -> definition == null ? "none" : definition.getClass().getSimpleName() }
def components = Components.getComponentProvider()
def definition = treeConstraint("promos")
// As GridView creates it
def constraint = components.newInstance(definition.getImplementationClass(), definition)
def magnolia = components.newInstance(definition.delegate.getImplementationClass(), definition.delegate)

def promos = MgnlContext.getJCRSession("promos")
def item = promos.getNode("/promo-plain")
def other = promos.getNode("/promo-ui")
def folder = promos.getNode("/promo-folder")
def container = promos.getNode("/promo-de/localeVariants")
def variant = promos.getNode("/promo-de/localeVariants/variant_de")
def ON = DropLocation.ON_TOP, BELOW = DropLocation.BELOW, ABOVE = DropLocation.ABOVE

def result = [
    promosConstraint: name(definition),
    promosDelegate: name(definition.delegate),
    itemIntoFolder: constraint.isAllowedAt(item, folder, ON),
    itemNextToItem: constraint.isAllowedAt(item, other, BELOW),
    itemIntoItsOwnParent: constraint.isAllowedAt(item, promos.getRootNode(), ON),
    sameAsMagnoliaOnOtherItems: [[item, folder, ON], [item, other, BELOW], [item, other, ON], [item, promos.getRootNode(), ON], [folder, item, ON], [folder, other, ABOVE]]
            .every { s, t, l -> constraint.isAllowedAt(s, t, l) == magnolia.isAllowedAt(s, t, l) },
    variantIntoFolder: constraint.isAllowedAt(variant, folder, ON),
    containerIntoFolder: constraint.isAllowedAt(container, folder, ON),
    // Several rows dragged together: one variant among them is enough
    itemAndVariantIntoFolder: constraint.isAllowedAt([item, variant], folder, ON),
    itemOnContainer: constraint.isAllowedAt(item, container, ON),
    itemNextToContainer: constraint.isAllowedAt(item, container, ABOVE),
    itemOnVariant: constraint.isAllowedAt(item, variant, ON),
    itemNextToVariant: constraint.isAllowedAt(item, variant, BELOW),
    magnoliaAloneVariantIntoFolder: magnolia.isAllowedAt(variant, folder, ON),
    magnoliaAloneItemOnContainer: magnolia.isAllowedAt(item, container, ON),
    ctasConstraint: name(treeConstraint("ctas")),
    promosHandWrittenConstraint: name(treeConstraint("promosHandWritten")),
    notEnabledConstraint: name(treeConstraint("promosStock"))
]
def json = { v -> v instanceof String ? '"' + v + '"' : String.valueOf(v) }
out.print('{' + result.collect { k, v -> '"' + k + '":' + json(v) }.join(',') + '}')
null
