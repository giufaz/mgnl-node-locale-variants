# Planned improvements

Work that is planned or under evaluation, most important first. Nothing here has a date. The limits these items address are described in [Known limits and risks](known-limits-and-risks.md).

## Next

1. **Choosers and link fields.** Verify what a chooser shows when it points to an app with locale variants, and keep containers and variants out of it if needed.
2. **No "Add language variant" on a variant's child nodes.** Don't offer the action on a node under a variant, where a variant can't be created.

## Later

3. **"Add language variant" only where there is more than one language.** Don't offer the action on a node whose site has a single locale: there is no other language to make a variant for.
4. **Import.** Tests, and fixes where needed, for importing an item with its variants.
5. **A faster "Localized" filter** for large workspaces.
6. **Dialogs out of the experimental state.** A dialog twin, like the one detail sub-apps have: node name read-only and no i18n field on a variant. It fixes the rename on save.
7. **Pages out of the experimental state**, once dialogs are: a decision on pages created under a page variant, and a check with a real front end.

## Under evaluation

Ideas for a later version, and open questions: points where it is not decided yet whether the module should handle them or today's behaviour is the right one. Nothing here is planned yet.

**Editing**

- **Variants with their sub-items.** Create a variant together with the child pages or items of the original, which the copy leaves out today.
- **A hint in the original's form** on the languages taken over by a variant.
- **Nothing foreign in a variants container.** The Move dialog can still put another node into a `localeVariants` container.
- **Showing what changed in the original** since a variant was created.

**Publishing**

- **"Publish incl. subnodes" without the variants.** Publish an original and its sub-items, and leave its variants for later.

**Delivery**

- **The original's identity on a served variant.** Serve a variant with the `@name` and `@path` of its original, so the front end doesn't see the difference.
- **A switch for the language fallback.** Serve a variant only for its own language (today `de` can get `variant_de_CH`).
- **Localized results for Delivery API queries:** filters, sorting and paging on the values that are served.
- **Server-side rendering.** Variants in FreeMarker pages: today only the Delivery API serves them.

## Release

- Version 0.2.0 is distributed from the repository (`dist/`). Next: define the release plan, for example Maven Central, GitHub Packages, or a release built by a GitHub Action.
