function fn() {
  var base = karate.properties['mgnl.base'] || 'http://localhost:8064';
  var config = {
    author: base + '/magnoliaAuthor',
    publicInstance: base + '/magnoliaPublic',
    projectVersion: karate.properties['project.version'],
    // AdminCentral login of the UI features (the fresh test instance's superuser)
    mgnlUser: karate.properties['mgnl.user'],
    mgnlPassword: karate.properties['mgnl.password']
  };
  karate.configure('headers', { Authorization: 'Basic ' + karate.properties['mgnl.authToken'] });
  // UI features: headless Chromium of the Karate image (started only by the features that use the driver)
  karate.configure('driver', { type: 'chrome', headless: true, timeout: 30000 });
  // Test content on author and public, once for all features
  // Absolute path: callSingle resolves a relative path from the first feature that runs (e.g. out-of-the-box/)
  config.seed = karate.callSingle('file:' + karate.properties['user.dir'] + '/seed.feature', config).seed;
  return config;
}
