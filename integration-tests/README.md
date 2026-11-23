# Integration tests

End-to-end regression tests on a real Magnolia CE (author + public in one Tomcat), written as [Karate](https://docs.karatelabs.io) features. Site, content type, page template, Delivery endpoints and the Groovy driver endpoint (`/.rest/lv-demo/groovy`, used by the seed) come from the project's `demo` module, installed with the module.

```bash
integration-tests/run.sh                    # build, start a fresh Magnolia, seed, run all features, stop and clean up
integration-tests/run.sh --tags=@delivery  # only some features (write --tags=@x: Karate reads a separate "@x" as an argument file) (tags: @install, @creator, @delivery, @references, @apps, @ui, @guard, @actions, @forms, @browser, @pages, @inside, @config, @ootb)
integration-tests/run.sh --keep             # leave Magnolia running afterwards (http://localhost:8064, superuser/superuser)
```

Needs only Docker: both images are built on first use, Magnolia from Magnolia's public Nexus (`env/image/`) and the Karate runner with headless Chromium (`env/karate/`), Maven is the installed `mvn`, or else runs in Docker too (`env/mvn.sh`; env `MVN` to choose another command). One Magnolia at a time: the run stops if another Magnolia container is up. A run also fails on any WARN/ERROR of the module in the author or public logs. Reports: `reports/<date_time>/karate-summary.html`, one folder per run; `reports/latest` points to the last one.

| Path | Content |
|---|---|
| `run.sh` | The whole run (env: `MGNL_PORT`, `MGNL_VERSION`, `MGNL_SUPERUSER_PASSWORD`, `MVN`, `KARATE_VERSION`) |
| `env/` | Docker Compose, Magnolia image and entrypoint (installs the module jar in author and public), Karate image, Maven in Docker (`mvn.sh`) |
| `features/seed.groovy` | Test content (demo pages and promos) made with the module's own components, on author and public |
| `features/*.feature` | The module's features: what it adds (`@ui`: in the AdminCentral, with Karate's browser driver) |
| `features/ui/admincentral.js` | AdminCentral steps for the UI features (login, open an app at a path, click an action, read a dialog), so the features read as user actions |
| `features/app-config-change.*` | Changes the module config at runtime (a decoration written into a light module of the test instance) and removes it afterwards |
| `features/out-of-the-box/*.feature` | Magnolia's own behaviour the module must not change (nodes without variants, requests out of scope, other APIs); a change here needs a deliberate decision |

Add a case: a new `Scenario` (or a row of an `Examples` table) in the matching feature; new test content goes in `seed.groovy`.
