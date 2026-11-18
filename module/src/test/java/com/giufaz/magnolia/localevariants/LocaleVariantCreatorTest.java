package com.giufaz.magnolia.localevariants;

import static com.giufaz.magnolia.localevariants.LocaleVariantStructure.VARIANTS_CONTAINER_NAME;
import static com.giufaz.magnolia.localevariants.LocaleVariantStructure.VARIANT_MIXIN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import info.magnolia.cms.i18n.DefaultI18nContentSupport;
import info.magnolia.cms.i18n.LocaleDefinition;
import info.magnolia.cms.security.User;
import info.magnolia.context.MgnlContext;
import info.magnolia.module.site.Site;
import info.magnolia.module.site.SiteManager;
import info.magnolia.test.mock.MockContext;
import info.magnolia.test.mock.MockNodeType;
import info.magnolia.test.mock.MockUtil;
import info.magnolia.test.mock.jcr.MockNode;
import info.magnolia.test.mock.jcr.MockProperty;
import info.magnolia.test.mock.jcr.MockValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocaleVariantCreatorTest {

    private static final Locale DE_CH = new Locale("de", "CH");

    private final SiteManager siteManager = mock(SiteManager.class);
    private final LocaleVariantStructure variantStructure = new LocaleVariantStructure(siteManager);
    private final LocaleVariantCreator creator = new LocaleVariantCreator(variantStructure);
    private MockNode root;
    private MockNode original;

    // Current user "eric"; one site with locales en (default), de, de_CH, fr; an orderable, versionable original
    @BeforeEach
    void setUp() throws RepositoryException {
        MockContext context = MockUtil.initMockContext();
        User user = mock(User.class);
        when(user.getName()).thenReturn("eric");
        context.setUser(user);

        DefaultI18nContentSupport i18n = new DefaultI18nContentSupport();
        i18n.setEnabled(true);
        i18n.setFallbackLocale(Locale.ENGLISH);
        i18n.setLocales(List.of(LocaleDefinition.make("en", null, true), LocaleDefinition.make("de", null, true),
                LocaleDefinition.make("de", "CH", true), LocaleDefinition.make("fr", null, true)));
        Site site = mock(Site.class);
        when(site.getName()).thenReturn("site");
        when(site.getI18n()).thenReturn(i18n);
        when(siteManager.getDefaultSite()).thenReturn(site);
        when(siteManager.getAssignedSite(any(Node.class))).thenReturn(site);

        root = new MockNode(); // the root node (depth 0)
        original = new MockNode("summer-sale", "my:promo");
        original.setPrimaryNodeType(new MockNodeType("my:promo", null, true)); // orderable, like mgnl:content
        root.addNode(original);
        original.addMixin("mgnl:versionable");
        original.setProperty("title", "Summer sale");
    }

    @AfterEach
    void tearDown() {
        MgnlContext.setInstance(null);
    }

    @Test
    void test_create_monoLingualProperties() throws RepositoryException {
        original.setProperty("title_de", "Sommerschlussverkauf");
        original.setProperty("title_fr", "Soldes d'été");
        original.setProperty("text", "Up to 50% off");
        original.setProperty("cta_fr", "Acheter");
        original.addProperty(multi("tags", "summer", "sale"));
        original.addProperty(multi("tags_de", "sommer"));

        Node variant = creator.create(original, Locale.GERMAN);

        assertEquals("Sommerschlussverkauf", string(variant, "title"), "title_de replaces title");
        assertEquals("Up to 50% off", string(variant, "text"), "no German value: the base stays");
        assertFalse(variant.hasProperty("cta"), "only a French value: nothing");
        assertEquals(List.of("sommer"), strings(variant, "tags"), "multi-valued");
        assertEquals(List.of(), localizedProperties(variant), "no *_<site locale> left");
    }

    @Test
    void test_create_countryVariantFallsBackToLanguage() throws RepositoryException {
        original.setProperty("title_de", "Sommerschlussverkauf");
        original.setProperty("text", "Up to 50% off");
        original.setProperty("text_de_CH", "Bis 50% günstiger");

        Node variant = creator.create(original, DE_CH);

        assertEquals("variant_de_CH", variant.getName());
        assertEquals("Bis 50% günstiger", string(variant, "text"), "_de_CH first");
        assertEquals("Sommerschlussverkauf", string(variant, "title"), "then _de");
        assertEquals(List.of(), localizedProperties(variant));
    }

    @Test
    void test_create_metadataAndMixins() throws RepositoryException {
        original.setProperty("mgnl:template", "my:promo");
        original.setProperty("mgnl:lastActivated", "2026-11-01");
        original.setProperty("jcr:createdBy", "admin");
        original.setProperty("rep:custom", "x");

        Node variant = creator.create(original, Locale.GERMAN);

        assertEquals("/summer-sale/localeVariants/variant_de", variant.getPath());
        assertEquals("my:promo", variant.getPrimaryNodeType().getName(), "original's primary type");
        assertTrue(variant.isNodeType("mgnl:versionable"), "original's mixins copied");
        assertTrue(variant.isNodeType(VARIANT_MIXIN));
        assertTrue(variantStructure.isValidVariant(variant));
        assertEquals("my:promo", string(variant, "mgnl:template"), "template kept");
        assertFalse(variant.hasProperty("mgnl:lastActivated"), "not published");
        assertFalse(variant.hasProperty("jcr:createdBy"));
        assertFalse(variant.hasProperty("rep:custom"));
        assertEquals("eric", string(variant, "mgnl:createdBy"), "user from the context");
        assertEquals("eric", string(variant, "mgnl:lastModifiedBy"));
        assertEquals(variant.getProperty("mgnl:created").getDate().getTimeInMillis(),
                variant.getProperty("mgnl:lastModified").getDate().getTimeInMillis(), "same instant");
    }

    @Test
    void test_create_childNodes() throws RepositoryException {
        Node address = original.addNode("address", "mgnl:contentNode");
        address.setProperty("street", "Main street");
        address.setProperty("street_de", "Hauptstrasse");
        address.setProperty("city", "Zurich");
        original.addNode("gallery", "mgnl:contentNode").setProperty("caption", "Beach");
        Node galleryDe = original.addNode("gallery_de", "mgnl:contentNode");
        galleryDe.setProperty("caption", "Strand");
        galleryDe.setProperty("caption_fr", "Plage");
        original.addNode("links_fr", "mgnl:contentNode").setProperty("label", "Lien");
        original.addNode("sub-promo", "mgnl:content");
        original.addNode("folder", "mgnl:folder");
        original.addNode("rep:policy", "rep:ACL");

        Node variant = creator.create(original, Locale.GERMAN);

        assertEquals("Hauptstrasse", string(variant.getNode("address"), "street"), "plain child: same rules");
        assertEquals("Zurich", string(variant.getNode("address"), "city"));
        assertFalse(variant.getNode("address").hasProperty("street_de"));
        assertEquals("Strand", string(variant.getNode("gallery"), "caption"), "gallery_de replaces gallery");
        assertEquals("Plage", string(variant.getNode("gallery"), "caption_fr"), "a localized child is copied as is");
        assertEquals(List.of("address", "gallery"), childNames(variant),
                "no other-locale children, items, folders, ACLs or container");
    }

    @Test
    void test_create_containerCreatedFirstAndReused() throws RepositoryException {
        original.addNode("main", "mgnl:contentNode");

        creator.create(original, Locale.GERMAN);
        creator.create(original, Locale.FRENCH);

        Node container = original.getNode(VARIANTS_CONTAINER_NAME);
        assertTrue(variantStructure.isVariantsContainer(container));
        assertEquals(List.of(VARIANTS_CONTAINER_NAME, "main"), childNames(original), "container first");
        assertEquals(List.of("variant_de", "variant_fr"), childNames(container), "one container for all variants");
    }

    @Test
    void test_create_containerLastWhenNotOrderable() throws RepositoryException {
        original.setPrimaryNodeType(new MockNodeType("my:promo", null, false));
        original.addNode("main", "mgnl:contentNode");

        creator.create(original, Locale.GERMAN);

        assertEquals(List.of("main", VARIANTS_CONTAINER_NAME), childNames(original));
    }

    @Test
    void test_create_invalidRequests() throws RepositoryException {
        Node notVersionable = root.addNode("teaser", "mgnl:contentNode");
        assertThrows(IllegalArgumentException.class, () -> creator.create(notVersionable, Locale.GERMAN), "not versionable");
        assertThrows(IllegalArgumentException.class, () -> creator.create(original, Locale.ENGLISH), "default locale");
        assertThrows(IllegalArgumentException.class, () -> creator.create(original, Locale.ITALIAN), "not a site locale");
        Node variant = creator.create(original, Locale.GERMAN);
        assertThrows(IllegalStateException.class, () -> creator.create(original, Locale.GERMAN), "exists");
        assertThrows(IllegalArgumentException.class, () -> creator.create(variant, Locale.FRENCH), "variant of a variant");
    }

    private static MockProperty multi(String name, String... values) throws RepositoryException {
        Value[] jcrValues = new Value[values.length];
        for (int i = 0; i < values.length; i++) {
            jcrValues[i] = new MockValue(values[i]);
        }
        return new MockProperty(name, jcrValues, null);
    }

    private static String string(Node node, String property) throws RepositoryException {
        return node.getProperty(property).getString();
    }

    private static List<String> strings(Node node, String property) throws RepositoryException {
        List<String> result = new ArrayList<>();
        for (Value value : node.getProperty(property).getValues()) {
            result.add(value.getString());
        }
        return result;
    }

    private static List<String> childNames(Node node) throws RepositoryException {
        List<String> names = new ArrayList<>();
        for (NodeIterator it = node.getNodes(); it.hasNext(); ) {
            names.add(it.nextNode().getName());
        }
        return names;
    }

    private static List<String> localizedProperties(Node node) throws RepositoryException {
        List<String> names = new ArrayList<>();
        for (PropertyIterator it = node.getProperties(); it.hasNext(); ) {
            String name = it.nextProperty().getName();
            if (name.matches(".*_(en|de|de_CH|fr)$")) {
                names.add(name);
            }
        }
        return names;
    }
}
