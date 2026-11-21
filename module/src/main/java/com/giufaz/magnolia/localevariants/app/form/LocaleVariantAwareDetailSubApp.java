package com.giufaz.magnolia.localevariants.app.form;

import info.magnolia.context.MgnlContext;
import info.magnolia.objectfactory.ComponentProvider;
import info.magnolia.ui.api.app.AppDescriptor;
import info.magnolia.ui.api.app.SubApp;
import info.magnolia.ui.api.app.SubAppContext;
import info.magnolia.ui.api.app.SubAppDescriptor;
import info.magnolia.ui.api.location.Location;
import info.magnolia.ui.api.view.View;
import info.magnolia.ui.contentapp.detail.ContentDetailSubApp;
import info.magnolia.ui.datasource.WithDatasource;
import info.magnolia.ui.datasource.jcr.JcrDatasourceDefinition;

import java.util.List;
import java.util.function.Supplier;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.giufaz.magnolia.localevariants.LocaleVariantStructure;

import jakarta.inject.Inject;

/**
 * Decorates a detail sub-app of an enabled app: set as its
 * {@code subAppClass}, it creates the sub-app's original class (Magnolia's or
 * a project's) and delegates everything to it.
 *
 * <p>
 * On a locale variant the original class gets the twin definition
 * {@code <subApp>_lvTwin}, built by the app definition transformer: the same
 * form with the node name read-only and no i18n field. On any other item: the
 * sub-app's original definition.
 */
public class LocaleVariantAwareDetailSubApp implements SubApp {

    public static final String TWIN_SUFFIX = "_lvTwin";

    private static final Logger log = LoggerFactory.getLogger(LocaleVariantAwareDetailSubApp.class);
    // React (Warp) detail forms are installed
    private static final boolean WARP_FORMS_INSTALLED = isClassPresent("info.magnolia.warp.engine.form.SelectedLocale");

    private final SubApp variantCompatibleDetailsApp;
    private final ComponentProvider componentProvider;
    private final boolean isVariant;

    @Inject
    public LocaleVariantAwareDetailSubApp(SubAppContext subAppContext, ComponentProvider componentProvider,
            LocaleVariantStructure variantStructure) {
        this.componentProvider = componentProvider;
        AppDescriptor appDef = subAppContext.getAppContext().getAppDescriptor();
        SubAppDescriptor origDetailAppDef = subAppContext.getSubAppDescriptor();
        SubAppDescriptor twinDetailAppDef = appDef.getSubApps().get(origDetailAppDef.getName() + TWIN_SUFFIX);

        String workspace = workspaceOf(origDetailAppDef, appDef);
        Location loc = subAppContext.getLocation();

        this.isVariant = twinDetailAppDef != null && pointsToVariant(loc, workspace, variantStructure);
        this.variantCompatibleDetailsApp = createVariantCompatibleDetailsApp(
                appDef,
                origDetailAppDef,
                twinDetailAppDef);
    }

    private SubApp createVariantCompatibleDetailsApp(
            AppDescriptor appDef,
            SubAppDescriptor origDetailAppDef,
            SubAppDescriptor twinDetailAppDef) {

        // The original DetailsApp class is kept in the twin
        // If the twin is not found, use a standard ContentDetailSubApp
        if (twinDetailAppDef == null) {
            var warnMsg = "[node-locale-variants] No twin for {}:{}: opened as {}";
            log.warn(warnMsg, appDef.getName(), origDetailAppDef.getName(), ContentDetailSubApp.class.getName());
            return componentProvider.newInstance(ContentDetailSubApp.class);
        }

        Class<? extends SubApp> origDetailsAppClass = twinDetailAppDef.getSubAppClass();
        // A definition passed as parameter wins over the one of the sub-app context
        if (isVariant) {
            var variantSpecificDetailsApp = componentProvider.newInstance(origDetailsAppClass, twinDetailAppDef);
            return variantSpecificDetailsApp;
        } else {
            var originalDetailsApp = componentProvider.newInstance(origDetailsAppClass);
            return originalDetailsApp;
        }
    }

    // A variant's React form must open in the default locale, whatever the editor
    // picked in other forms of the app
    @Override
    public View start(Location location) {
        if (isVariant && WARP_FORMS_INSTALLED) {
            Supplier<View> appViewOpener = () -> variantCompatibleDetailsApp.start(location);
            return WarpFormLocale.withDefaultLocale(componentProvider, appViewOpener);
        } else {
            return variantCompatibleDetailsApp.start(location);
        }
    }

    // --- Everything else goes to the delegated variant compatible details app ---

    @Override
    public String getCaption() {
        return variantCompatibleDetailsApp.getCaption();
    }

    @Override
    public void stop() {
        variantCompatibleDetailsApp.stop();
    }

    @Override
    public void locationChanged(Location location) {
        variantCompatibleDetailsApp.locationChanged(location);
    }

    @Override
    public String getSubAppId() {
        return variantCompatibleDetailsApp.getSubAppId();
    }

    @Override
    public boolean supportsLocation(Location location) {
        return variantCompatibleDetailsApp.supportsLocation(location);
    }

    @Override
    public View getView() {
        return variantCompatibleDetailsApp.getView();
    }

    @Override
    public boolean isCloseable() {
        return variantCompatibleDetailsApp.isCloseable();
    }

    @Override
    public String getIcon(Location location) {
        return variantCompatibleDetailsApp.getIcon(location);
    }

    // --- Helpers ---

    private static boolean pointsToVariant(
            Location location,
            String workspace,
            LocaleVariantStructure variantStructure) {

        var detailsAppLocation = ContentDetailSubApp.DetailLocation.wrap(location);
        String nodePath = detailsAppLocation.getNodePath();
        boolean isNewNode = ContentDetailSubApp.VIEW_TYPE_ADD.equals(detailsAppLocation.getViewType());

        if (workspace == null || nodePath == null || isNewNode) {
            return false;
        }
        try {
            Session session = MgnlContext.getJCRSession(workspace);
            return session.nodeExists(nodePath) && variantStructure.isValidVariant(session.getNode(nodePath));
        } catch (RepositoryException | RuntimeException e) {
            var warnMsg = "[node-locale-variants] Can't read {}:{}: form left as the app defines it";
            log.warn(warnMsg, workspace, nodePath, e);
            return false;
        }
    }

    // The sub-app's own datasource, else the app's
    private static String workspaceOf(SubAppDescriptor origDetailAppDef, AppDescriptor appDef) {
        for (Object def : List.of(origDetailAppDef, appDef)) {
            if (def instanceof WithDatasource<?> withDatasource
                    && withDatasource.getDatasource() instanceof JcrDatasourceDefinition jcrDatasource) {
                return jcrDatasource.getWorkspace();
            }
        }
        return null;
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, LocaleVariantAwareDetailSubApp.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
