// Changes the module config at runtime with a decoration in a light module of the instance's resources dir
// (ACTION: write | remove | read), then prints what the module has applied, as a JSON object.
import info.magnolia.init.MagnoliaConfigurationProperties
import info.magnolia.objectfactory.Components
import info.magnolia.ui.api.app.registry.AppDescriptorRegistry
import com.giufaz.magnolia.localevariants.app.activation.AppActivation

def action = "__ACTION__"
def lightModule = new File(Components.getComponent(MagnoliaConfigurationProperties).getProperty("magnolia.resources.dir"), "lv-it-config")
if (action == "write") {
    def dir = new File(lightModule, "decorations/node-locale-variants")
    dir.mkdirs()
    // Promos: only "export" on containers and one more action on variants; ctas: switched off
    new File(dir, "it.config.yaml").text = """\
apps:
  promos:
    containerActions: [export]
    replaceDefaultContainerActions: true
  ctas:
    enabled: false
"""
} else if (action == "remove") {
    lightModule.deleteDir()
}

def activation = Components.getComponent(AppActivation)
def registry = Components.getComponent(AppDescriptorRegistry)
def decorated = { String app -> registry.getProvider(app).getDecorators().any { it.toString().contains("node-locale-variants") } }
def promos = activation.getAppliedApp("promos")
def containerButtons = registry.getProvider("promos").get().getSubApps().get("browser").getActionbar().getSections()
        .find { it.name == "localeVariants" }?.groups?.collectMany { it.items*.name } ?: []
def result = [
    promosContainerActions: promos == null ? "none" : promos.containerActions().join(","),
    promosContainerButtons: containerButtons.join(","),
    variantDialogs: promos != null && promos.experimentalVariantDialogs(),
    ctasApplied: activation.getAppliedApp("ctas") != null,
    ctasDecorated: decorated("ctas"),
    promosDecorated: decorated("promos")
]
def json = { v -> v instanceof String ? '"' + v + '"' : String.valueOf(v) }
out.print('{' + result.collect { k, v -> '"' + k + '":' + json(v) }.join(',') + '}')
null
