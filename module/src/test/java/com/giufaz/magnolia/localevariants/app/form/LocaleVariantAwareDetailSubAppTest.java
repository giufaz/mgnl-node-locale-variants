package com.giufaz.magnolia.localevariants.app.form;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import info.magnolia.context.MgnlContext;
import info.magnolia.module.site.SiteManager;
import info.magnolia.objectfactory.ComponentProvider;
import info.magnolia.test.mock.MockContext;
import info.magnolia.test.mock.jcr.MockNode;
import info.magnolia.ui.api.app.AppContext;
import info.magnolia.ui.api.app.SubApp;
import info.magnolia.ui.api.app.SubAppContext;
import info.magnolia.ui.api.app.SubAppDescriptor;
import info.magnolia.ui.api.location.DefaultLocation;
import info.magnolia.ui.api.location.Location;
import info.magnolia.ui.api.view.View;
import info.magnolia.ui.contentapp.configuration.ContentAppDescriptor;
import info.magnolia.ui.contentapp.detail.ContentDetailSubApp;
import info.magnolia.ui.contentapp.detail.DetailDescriptor;
import info.magnolia.ui.datasource.jcr.JcrDatasourceDefinition;
import info.magnolia.warp.engine.form.SelectedLocale;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.giufaz.magnolia.localevariants.LocaleVariantStructure;

class LocaleVariantAwareDetailSubAppTest {

    private static final String VARIANT_PATH = "/summer-sale/localeVariants/variant_de";
    private static final String ORIGINAL_PATH = "/summer-sale";

    // The sub-app class the app had before the decoration
    private interface ProjectDetailSubApp extends SubApp {
    }

    private final ComponentProvider componentProvider = mock(ComponentProvider.class);
    private final LocaleVariantStructure variantStructure = new LocaleVariantStructure(mock(SiteManager.class));
    private final SubAppContext subAppContext = mock(SubAppContext.class);
    private final ContentAppDescriptor<JcrDatasourceDefinition> appDef = mock(ContentAppDescriptor.class);
    private final DetailDescriptor<Node, JcrDatasourceDefinition> origDetailAppDef = mock(DetailDescriptor.class);
    private final DetailDescriptor<Node, JcrDatasourceDefinition> twinDetailAppDef = mock(DetailDescriptor.class);
    private final Map<String, SubAppDescriptor> subApps = new LinkedHashMap<>();
    private final Session session = mock(Session.class);
    private final SelectedLocale selectedLocale = mock(SelectedLocale.class);

    // What the component provider creates: the original class with the twin
    // definition, with its own definition, or Magnolia's standard class
    private final ProjectDetailSubApp withTwinDefinition = mock(ProjectDetailSubApp.class);
    private final ProjectDetailSubApp withOriginalDefinition = mock(ProjectDetailSubApp.class);
    private final ContentDetailSubApp<?> standardDetailSubApp = mock(ContentDetailSubApp.class);

    // App "promos" on the workspace "promos", with the sub-app "detail" and its
    // twin; a "de" variant of /summer-sale
    @BeforeEach
    void setUp() throws RepositoryException {
        MockContext context = new MockContext();
        context.addSession("promos", session);
        MgnlContext.setInstance(context);

        Node original = new MockNode().addNode("summer-sale", "lvd:promo");
        Node variant = original.addNode("localeVariants", "lv:localeVariants").addNode("variant_de", "lvd:promo");
        variant.addMixin(LocaleVariantStructure.VARIANT_MIXIN);
        when(session.nodeExists(VARIANT_PATH)).thenReturn(true);
        when(session.getNode(VARIANT_PATH)).thenReturn(variant);
        when(session.nodeExists(ORIGINAL_PATH)).thenReturn(true);
        when(session.getNode(ORIGINAL_PATH)).thenReturn(original);

        JcrDatasourceDefinition datasource = new JcrDatasourceDefinition();
        datasource.setWorkspace("promos");
        when(appDef.getName()).thenReturn("promos");
        when(appDef.getDatasource()).thenReturn(datasource);
        when(appDef.getSubApps()).thenReturn(subApps);
        when(origDetailAppDef.getName()).thenReturn("detail");
        doReturn(ProjectDetailSubApp.class).when(twinDetailAppDef).getSubAppClass();
        subApps.put("detail", origDetailAppDef);
        subApps.put("detail_lvTwin", twinDetailAppDef);

        AppContext appContext = mock(AppContext.class);
        when(appContext.getAppDescriptor()).thenReturn(appDef);
        when(subAppContext.getAppContext()).thenReturn(appContext);
        when(subAppContext.getSubAppDescriptor()).thenReturn(origDetailAppDef);

        when(componentProvider.newInstance(ProjectDetailSubApp.class, twinDetailAppDef)).thenReturn(withTwinDefinition);
        when(componentProvider.newInstance(ProjectDetailSubApp.class)).thenReturn(withOriginalDefinition);
        doReturn(standardDetailSubApp).when(componentProvider).newInstance(ContentDetailSubApp.class);
        when(componentProvider.getComponent(SelectedLocale.class)).thenReturn(selectedLocale);
    }

    @AfterEach
    void tearDown() {
        MgnlContext.setInstance(null);
    }

    // --- Which class, with which definition ---

    @Test
    void test_onVariant_originalClassWithTwinDefinition() {
        Location location = open(VARIANT_PATH + ":edit");
        View view = mock(View.class);
        when(withTwinDefinition.start(location)).thenReturn(view);

        LocaleVariantAwareDetailSubApp subApp = newSubApp();

        assertSame(view, subApp.start(location));
        verify(componentProvider, never()).newInstance(ProjectDetailSubApp.class);
    }

    @Test
    void test_onOriginal_originalClassWithItsOwnDefinition() {
        Location location = open(ORIGINAL_PATH + ":edit");
        View view = mock(View.class);
        when(withOriginalDefinition.start(location)).thenReturn(view);

        LocaleVariantAwareDetailSubApp subApp = newSubApp();

        assertSame(view, subApp.start(location));
        verify(componentProvider, never()).newInstance(ProjectDetailSubApp.class, twinDetailAppDef);
        verifyNoInteractions(selectedLocale);
    }

    // The path of a new item is its parent's: a new item under a variant is not
    // a variant
    @Test
    void test_onNewItem_ownDefinition() {
        Location location = open(VARIANT_PATH + ":add");

        newSubApp().start(location);

        verify(withOriginalDefinition).start(location);
    }

    @Test
    void test_onMissingNode_ownDefinition() {
        Location location = open("/gone:edit");

        newSubApp().start(location);

        verify(withOriginalDefinition).start(location);
    }

    @Test
    void test_onRepositoryError_ownDefinition() throws RepositoryException {
        when(session.nodeExists(VARIANT_PATH)).thenThrow(new RepositoryException("down"));
        Location location = open(VARIANT_PATH + ":edit");

        newSubApp().start(location);

        verify(withOriginalDefinition).start(location);
    }

    // The sub-app's own datasource wins over the app's
    @Test
    void test_workspaceOfTheSubApp() throws RepositoryException {
        JcrDatasourceDefinition own = new JcrDatasourceDefinition();
        own.setWorkspace("archive");
        when(origDetailAppDef.getDatasource()).thenReturn(own);
        Session archive = mock(Session.class);
        ((MockContext) MgnlContext.getInstance()).addSession("archive", archive);
        Location location = open(VARIANT_PATH + ":edit");

        newSubApp().start(location);

        verify(archive).nodeExists(VARIANT_PATH);
        verify(withOriginalDefinition).start(location);
        verifyNoInteractions(session);
    }

    // An app that is not on JCR: nothing to look at
    @Test
    void test_withoutJcrDatasource_ownDefinition() {
        when(appDef.getDatasource()).thenReturn(null);
        Location location = open(VARIANT_PATH + ":edit");

        newSubApp().start(location);

        verify(withOriginalDefinition).start(location);
        verifyNoInteractions(session);
    }

    // The class set without the twin (not by the module's transformer): the
    // original class is unknown, Magnolia's standard one opens
    @Test
    void test_withoutTwin_standardDetailSubApp() {
        subApps.remove("detail_lvTwin");
        Location location = open(VARIANT_PATH + ":edit");

        newSubApp().start(location);

        verify(standardDetailSubApp).start(location);
        verifyNoInteractions(session, selectedLocale);
    }

    // --- React (Warp) forms ---

    // The editor picked "fr" in another form of the app: the variant's form
    // starts with no remembered locale, then "fr" is put back
    @Test
    void test_start_onVariant_withTheDefaultLocale() {
        Location location = open(VARIANT_PATH + ":edit");
        when(selectedLocale.getLocale()).thenReturn("fr");

        newSubApp().start(location);

        InOrder order = inOrder(selectedLocale, withTwinDefinition);
        order.verify(selectedLocale).setLocale(null);
        order.verify(withTwinDefinition).start(location);
        order.verify(selectedLocale).setLocale("fr");
    }

    @Test
    void test_start_onVariant_localePutBackAfterAnError() {
        Location location = open(VARIANT_PATH + ":edit");
        when(selectedLocale.getLocale()).thenReturn("fr");
        when(withTwinDefinition.start(location)).thenThrow(new IllegalStateException("form"));

        assertThrows(IllegalStateException.class, () -> newSubApp().start(location));

        verify(selectedLocale).setLocale("fr");
    }

    // --- Everything else ---

    @Test
    void test_delegation() {
        Location location = open(ORIGINAL_PATH + ":edit");
        Location other = new DefaultLocation(Location.LOCATION_TYPE_APP, "promos", "detail", "/winter-sale:edit");
        View view = mock(View.class);
        when(withOriginalDefinition.getCaption()).thenReturn("Summer sale");
        when(withOriginalDefinition.getSubAppId()).thenReturn("detail");
        when(withOriginalDefinition.supportsLocation(location)).thenReturn(true);
        when(withOriginalDefinition.getView()).thenReturn(view);
        when(withOriginalDefinition.isCloseable()).thenReturn(true);
        when(withOriginalDefinition.getIcon(location)).thenReturn("icon-edit");

        LocaleVariantAwareDetailSubApp subApp = newSubApp();

        assertEquals("Summer sale", subApp.getCaption());
        assertEquals("detail", subApp.getSubAppId());
        assertTrue(subApp.supportsLocation(location));
        assertFalse(subApp.supportsLocation(other));
        assertSame(view, subApp.getView());
        assertTrue(subApp.isCloseable());
        assertEquals("icon-edit", subApp.getIcon(location));
        subApp.locationChanged(other);
        verify(withOriginalDefinition).locationChanged(other);
        subApp.stop();
        verify(withOriginalDefinition).stop();
    }

    // The sub-app opens on the location of its context
    private Location open(String parameter) {
        Location location = new DefaultLocation(Location.LOCATION_TYPE_APP, "promos", "detail", parameter);
        when(subAppContext.getLocation()).thenReturn(location);
        return location;
    }

    private LocaleVariantAwareDetailSubApp newSubApp() {
        return new LocaleVariantAwareDetailSubApp(subAppContext, componentProvider, variantStructure);
    }
}
