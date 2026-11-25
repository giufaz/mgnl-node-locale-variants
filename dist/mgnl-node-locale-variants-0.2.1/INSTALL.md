# mgnl-node-locale-variants 0.2.1: install from these files

Language variants of content items and pages for **Magnolia 6.4.x** (Java 17+). The module is not on Maven Central
yet: these files let you install it without building it.

Files:
- `mgnl-node-locale-variants-0.2.1.jar`: the module
- `mgnl-node-locale-variants-0.2.1.pom`: its pom
- `mgnl-node-locale-variants-0.2.1-sources.jar`: the sources (optional, for the IDE)

## 1. Put it in your local Maven repository

From the folder with the three files.

Linux, macOS, Windows Command Prompt:

```
mvn install:install-file -Dfile=mgnl-node-locale-variants-0.2.1.jar -DpomFile=mgnl-node-locale-variants-0.2.1.pom -Dsources=mgnl-node-locale-variants-0.2.1-sources.jar
```

Windows PowerShell (`--%` keeps PowerShell from splitting the `-D` arguments at the dots):

```
mvn --% install:install-file -Dfile=mgnl-node-locale-variants-0.2.1.jar -DpomFile=mgnl-node-locale-variants-0.2.1.pom -Dsources=mgnl-node-locale-variants-0.2.1-sources.jar
```

It ends up in `~/.m2/repository/io/github/giufaz/mgnl-node-locale-variants/0.2.1/`, as if it had been
downloaded. On a build server, or for a team, upload the same three files to your own Maven repository instead.

## 2. Add the dependency

In the pom of the webapp that builds the Magnolia war (author and public):

```xml
<dependency>
  <groupId>io.github.giufaz</groupId>
  <artifactId>mgnl-node-locale-variants</artifactId>
  <version>0.2.1</version>
</dependency>
```

Build and start Magnolia: the module `node-locale-variants` installs itself (confirm the web installer if it asks).
It registers two node types; nothing else is written, and no app changes until you enable it.

Without Maven: copy the jar into `WEB-INF/lib` of the author and public webapps. It needs no other library.

## 3. Turn it on for an app

The site must have i18n enabled with at least two locales.

In any of your modules (Maven or light module), create `decorations/node-locale-variants/config.yaml`:

```yaml
apps:
  my-content-app:               # the app name
    nodeTypes: [myns:myItem]    # node type(s) of the app's items (content types: the model's nodeType)
```

The change applies in a few seconds, without a restart. In the app, select an item and click
**Add language variant**.

Headless: `/.rest/delivery/<endpoint>/<path>?lang=de` returns the `de` variant in place of the original. On the
public instance the variants must be published: "Publish incl. subnodes" on the original does it in one step.

All the options, the experimental Pages preset and the known limits: see the README of the project,
https://github.com/giufaz/mgnl-node-locale-variants
