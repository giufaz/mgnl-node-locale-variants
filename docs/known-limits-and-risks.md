# Known limits and risks

This page lists the limits and risks of the module: what it does not do today, what to watch for, and the limits that are there by design, as a result of the decisions taken for this project. Planned work on these points is in [Planned improvements](planned-improvements.md).

## Scope

- **Magnolia 6.4 only.** The module uses the `jakarta` APIs of Magnolia 6.4. There is no 6.3 (`javax`) build.
- **Built and tested on the Community Edition 6.4.10.** Development, unit tests and integration tests all run on Magnolia Community Edition 6.4.10.
- **Tested by hand on DX Core.** The module was installed and tested manually in a project based on Magnolia DX Core 6.4. Its behaviour next to DX Core features (workflow, personalization, multisite, content translation) is not battle-tested.
- **A variant's language comes from the site definition.** A variant can be created for any locale of the site the node belongs to, except the default one, which always belongs to the original node.
- **Only nodes that stand on their own can have variants:** versionable nodes such as pages and content items, not components or nested nodes. Variants are never nested.

## Delivery API

- **Only the Delivery API v2** (`jcrDeliveryEndpoint_v2`, `jcrPagesDeliveryEndpoint_v2`). The GraphQL API, the nodes API and FreeMarker rendering serve the original.
- **A served variant keeps its own identity.** The JSON of a variant has the variant's `@name` (`variant_de`), `@path` (`/summer-sale/localeVariants/variant_de`) and `@id`, not the original's. A front end that uses the name or the path of a node, for example to build URLs or to match a route, gets different values for a language that has a variant.
- **Queries are not localized.** Searches and filtered lists return the originals as out of the box; only a single node, the children of a node (`@nodes`) and references are swapped.
- **Requests without a language, with `lang=all` or for a version** get the original.
- **Language fallback serves a whole variant.** A request for `de_CH` on an item that has only a `de` variant gets the `de` variant, not the original: the original's own `de_CH` values (`title_de_CH`) are then never served. The same holds the other way round: a request for `de` on an item that has only a `de_CH` variant gets that variant. Create a variant for every language that must not fall back.
- **References rely on a Magnolia internal.** To tell which reference resolvers are JCR resolvers, the module looks at a private wrapper class of Magnolia's Delivery endpoint. It checks it at every start: if a Magnolia update changes it, the module logs an ERROR and references are served as out of the box, while everything else keeps working. The check is one class (`JcrReferenceResolverDetector`) that a project can replace in its module descriptor.

## Content apps

- **What a new variant contains.** A variant starts as a copy of its original: properties and nested nodes (areas, components, nested content nodes) are copied, with the variant's language as the only language at every level of nesting. Child pages, child items and folders are **not** copied: a variant of a page does not get the page's subpages. The rule goes by node type (`mgnl:content` and `mgnl:folder`, with their subtypes), not by the `mgnl:versionable` mixin: a custom node type that is versionable without extending `mgnl:content` would be copied. A project can change the rules by replacing the component `LocaleVariantCreator`.
- **A variant does not follow its original.** It is a copy taken when the variant is created: later changes to the original are not carried over, and nothing shows what differs.
- **A node created under a variant is an ordinary node.** If your app lets editors add an item under another item, an item added under a variant keeps the app's own form, with every language, and all its actions. This is on purpose: the item can be moved out of the variant. Two loose ends: "Add language variant" is offered on it although a variant can't be created there, and with the experimental dialogs on it is shown in one language in dialogs and in the page editor (a page added under a page variant, for example).
- **Import is not covered.** Exporting works on originals, containers and variants; importing an item with its variants, or a variant next to another original, has not been tested.
- **Choosers and link fields that point to an app with variants** have not been tested: they may show containers and variants as selectable items.
- **The "Localized" filter** checks every item for a container, so it gets slower as the workspace grows.
- **Module config lists are replaced item by item by a decoration.** To add one action to a list that already has values, the whole list has to be repeated.

## Experimental: dialogs, page editor and the Pages app

Off by default (`defaults.experimentalVariantDialogs`). With it on, a variant is shown in one language in Vaadin dialogs and in the page editor, and the Pages preset (`apps.pages-app`) can be used. Known defects and things to know:

- **A dialog with a node-name field renames the variant on save.** The variant then loses its name `variant_<locale>` and is no longer a variant. Detail sub-apps are not affected: there the name is read-only. Until this is fixed, don't allow such a dialog's action on variants.
- **Opening a variant switches the app's editing language.** Magnolia keeps one current language per app: opening a variant's dialog sets it to the variant's language, as if the editor had picked it in the language selector, and the next dialog of an original opens in that language ("Title (de)"). The editor switches back with the selector. A dialog whose footer leaves out Magnolia's `localeSelector` gives no way to switch back: keep the selector in dialogs with i18n fields.
- **The page editor needs a front end that accepts the variant's path.** A variant's page lives at `<page>/localeVariants/variant_<locale>`: a front end that only routes known page URLs will not show it in the editor.
- **Not allowed on page variants by the preset:** change template, add page, import, publish incl. subpages.
