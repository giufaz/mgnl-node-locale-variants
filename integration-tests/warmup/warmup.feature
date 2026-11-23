Feature: Warm-up, run by run.sh before the scenarios

  # On a Magnolia that has just started, the first login and the first app take longer to open than a scenario
  # waits. This opens them with patience, so that the scenarios don't fail for it; if it can't within the time given
  # (run.sh: WARMUP_TIMEOUT), run.sh stops before the scenarios and says so.
  # Not in features/: it is not one of the scenarios.

  Scenario: the AdminCentral opens an app
    * def ui = call read('file:' + karate.properties['user.dir'] + '/ui/admincentral.js')
    * ui.warmUp(parseInt(karate.properties['warmup.seconds']))
