// Test content, created with the module's own components on the instance that runs it (author or public).
// Uses the demo's page template and promo content type. Prints a JSON object with values the features check.
import info.magnolia.context.MgnlContext
import info.magnolia.jcr.util.NodeTypes
import info.magnolia.objectfactory.Components
import com.giufaz.magnolia.localevariants.LocaleVariantCreator

def creator = Components.getComponent(LocaleVariantCreator)
def result = [:]
def DE = Locale.GERMAN
def DE_CH = new Locale("de", "CH")

def reset = { session, List<String> names ->
    names.each { if (session.getRootNode().hasNode(it)) session.getRootNode().getNode(it).remove() }
    session.save()
}

// Pages (website)
def web = MgnlContext.getJCRSession("website")
reset(web, ["it-page", "it-areas", "it-plain", "it-status", "it-parent", "it-ui", "it-ui-create", "it-ui-props",
        "it-ui-component", "it-ui-component-language", "it-ui-subpage"])
def page = { String name, String title ->
    def p = web.getRootNode().addNode(name, "mgnl:page")
    p.setProperty("mgnl:template", "node-locale-variants-demo:pages/basic")
    p.setProperty("title", title)
    p
}
// it-page: i18n title, an existing area (the container must go before it) and a "de" variant
def itPage = page("it-page", "Hello")
itPage.setProperty("title_de", "Hallo")
itPage.addNode("main", "mgnl:area")
// it-areas: no area node, so the page variant gets its areas generated at delivery time
def itAreas = page("it-areas", "Areas")
// it-plain: no variants, two child pages (out-of-the-box checks)
def itPlain = page("it-plain", "Plain")
itPlain.setProperty("title_de", "Schlicht")
itPlain.addNode("c1", "mgnl:page").setProperty("title", "One")
itPlain.addNode("c2", "mgnl:page").setProperty("title", "Two")
def itStatus = page("it-status", "Status")
// it-parent: two child pages, the first with a "de" variant (@nodes on the pages endpoint)
def itParent = page("it-parent", "Parent")
def childA = itParent.addNode("child-a", "mgnl:page")
childA.setProperty("mgnl:template", "node-locale-variants-demo:pages/basic")
childA.setProperty("title", "Child A")
itParent.addNode("child-b", "mgnl:page").setProperty("title", "Child B")
// Pages app scenarios (app-pages): it-ui has a "de" variant and a child page; it-ui-create gets its variant from the
// app; the others have a "de" variant: it-ui-props for the page properties dialog, it-ui-component and
// it-ui-component-language for the page editor, it-ui-subpage for a page added under a page variant
def uiPages = ["it-ui", "it-ui-create", "it-ui-props", "it-ui-component", "it-ui-component-language",
        "it-ui-subpage"].collect {
    def p = page(it, it)
    p.setProperty("title_de", it + " (de)")
    p
}
uiPages[0].addNode("child", "mgnl:page").setProperty("mgnl:template", "node-locale-variants-demo:pages/basic")
uiPages[1].addNode("child", "mgnl:page").setProperty("mgnl:template", "node-locale-variants-demo:pages/basic")
web.save()
creator.create(itPage, DE)
(uiPages - uiPages[1]).each { creator.create(it, DE) }
creator.create(itAreas, DE)
creator.create(childA, DE)
web.save()
result.variantMixins = itPage.getNode("localeVariants/variant_de").getMixinNodeTypes()*.name

// Promos (demo content type)
def promos = MgnlContext.getJCRSession("promos")
reset(promos, ["promo-de", "promo-ch", "promo-plain", "promo-status", "promo-ref", "promo-ref-plain", "promo-ref-variant", "promo-n1", "promo-n2", "promo-x", "promo-y", "promo-ui", "promo-drag", "promo-folder",
        "promo-publish", "promo-publish-steps", "promo-delete", "promo-delete-published", "promo-delete-container",
        "promo-export", "promo-edit", "promo-versions", "promo-form", "promo-restore-container", "promo-restore-original"])
def promo = { String name, String title ->
    def p = promos.getRootNode().addNode(name, "lvd:promo")
    p.setProperty("name", name)
    p.setProperty("title", title)
    p
}
def promoDe = promo("promo-de", "Hello")
promoDe.setProperty("title_de", "Hallo")
def promoCh = promo("promo-ch", "Swiss only")
def promoPlain = promo("promo-plain", "Plain")
promoPlain.setProperty("title_de", "Schlicht")
def promoStatus = promo("promo-status", "Status")
// promo-ui: no variants, the UI features create them from the Promos app
def promoUi = promo("promo-ui", "UI")
promoUi.setProperty("title_de", "Oberfläche")
// promo-drag and promo-folder: the drag and drop feature moves the first into the second
promo("promo-drag", "Drag")
promos.getRootNode().addNode("promo-folder", "mgnl:folder")
// One item with a "de" variant per area of the stock actions feature (app-variant-actions): each scenario changes its own
// Only on author: they reach public when the scenarios publish them
def onAuthor = Components.getComponent(info.magnolia.cms.beans.config.ServerConfiguration).isAdmin()
def actionItems = !onAuthor ? [] : ["promo-publish", "promo-publish-steps", "promo-delete", "promo-delete-published",
        "promo-delete-container", "promo-export", "promo-edit", "promo-versions", "promo-form",
        "promo-restore-container", "promo-restore-original"].collect { promo(it, "Original") }
promos.save()
creator.create(promoDe, DE)
creator.create(promoCh, DE_CH)
actionItems.each { creator.create(it, DE) }
promos.save()

// References ("related", UUID): to an original with variants, to one without, to a variant;
// a chain n1 -> n2 -> promo-de (nested), and a cycle x <-> y where both have a "de" variant
def related = { from, to -> from.setProperty("related", to.getIdentifier()) }
related(promo("promo-ref", "Ref"), promoDe)
related(promo("promo-ref-plain", "Ref plain"), promoPlain)
related(promo("promo-ref-variant", "Ref variant"), promoDe.getNode("localeVariants/variant_de"))
def n1 = promo("promo-n1", "N1"); def n2 = promo("promo-n2", "N2")
related(n1, n2); related(n2, promoDe)
def x = promo("promo-x", "X"); def y = promo("promo-y", "Y")
promos.save()
related(x, y); related(y, x)
promos.save()
creator.create(x, DE)
creator.create(y, DE)
promos.save()

// CTAs (hand-written multi-type app "ctas"): social and link are enabled types with a "de" variant, contact is not
def ctas = MgnlContext.getJCRSession("ctas")
reset(ctas, ["cta-social", "cta-link", "cta-contact", "cta-banner", "cta-inside"])
def cta = { String name, String type ->
    def n = ctas.getRootNode().addNode(name, type)
    n.setProperty("name", name)
    n.setProperty("label", name)
    n
}
def ctaSocial = cta("cta-social", "lvd:ctaSocial")
def ctaLink = cta("cta-link", "lvd:ctaLink")
cta("cta-contact", "lvd:ctaContact")
// cta-banner: edited in a dialog (openDialogAction), with an i18n label and a "de" variant
def ctaBanner = cta("cta-banner", "lvd:ctaBanner")
ctaBanner.setProperty("label_de", "cta-banner (de)")
ctas.save()
creator.create(ctaSocial, DE)
creator.create(ctaLink, DE)
creator.create(ctaBanner, DE)
// cta-inside: items created under its "de" variant (app-inside-variant), one edited in a form, one in a dialog
def ctaInside = cta("cta-inside", "lvd:ctaSocial")
ctas.save()
creator.create(ctaInside, DE)
ctas.save()
["link-inside": "lvd:ctaLink", "banner-inside": "lvd:ctaBanner"].each { name, type ->
    def child = ctaInside.getNode("localeVariants/variant_de").addNode(name, type)
    child.setProperty("name", name)
    child.setProperty("label", name)
}
ctas.save()

// Published original, then its first and second variant: on a page and on a content item
def lastModified = { n -> NodeTypes.LastModified.getLastModified(n)?.timeInMillis }
def statusFlow = { node, String key ->
    def session = node.getSession()
    def user = MgnlContext.getUser().getName()
    NodeTypes.Activatable.update(node, user, true); session.save()
    result[key + "Before"] = NodeTypes.Activatable.getActivationStatus(node)
    Thread.sleep(1100)
    creator.create(node, DE); session.save()
    result[key + "AfterFirstVariant"] = NodeTypes.Activatable.getActivationStatus(node)
    def container = node.getNode("localeVariants")
    result[key + "ContainerCreatedBy"] = container.hasProperty("mgnl:createdBy") ? container.getProperty("mgnl:createdBy").string : "none"
    result[key + "ContainerHasDates"] = container.hasProperty("mgnl:created") && container.hasProperty("mgnl:lastModified")
    def containerModified = lastModified(container)
    NodeTypes.Activatable.update(node, user, true); session.save()
    Thread.sleep(1100)
    creator.create(node, DE_CH); session.save()
    result[key + "AfterSecondVariant"] = NodeTypes.Activatable.getActivationStatus(node)
    result[key + "ContainerTouchedBySecondVariant"] = lastModified(container) != containerModified
}
statusFlow(itStatus, "page")
statusFlow(promoStatus, "item")

// groovy-json is not in the webapp: a flat object of booleans, numbers, strings and string lists is enough
def json = { v -> v instanceof List ? '[' + v.collect { '"' + it + '"' }.join(',') + ']' : v instanceof String ? '"' + v + '"' : String.valueOf(v) }
out.print('{' + result.collect { k, v -> '"' + k + '":' + json(v) }.join(',') + '}')
null
