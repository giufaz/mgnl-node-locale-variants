# Karate test runner (standalone jar, needs Java 21) with Chromium for the UI features (Karate's CDP driver).
# Debian, not Ubuntu: Ubuntu's chromium package is a snap, which doesn't run in Docker. Built once by ../../run.sh.
FROM debian:trixie-slim
ARG KARATE_VERSION=2.1.4
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends openjdk-21-jre-headless chromium fonts-dejavu-core ca-certificates curl; \
    rm -rf /var/lib/apt/lists/*; \
    curl -fsSL -o /opt/karate.jar "https://github.com/karatelabs/karate/releases/download/v${KARATE_VERSION}/karate-${KARATE_VERSION}.jar"; \
    chmod a+r /opt/karate.jar
# Headless Chromium as a non-root user in a container; a window tall enough to show the whole action bar
ENV KARATE_CHROME_EXECUTABLE=/usr/bin/chromium \
    KARATE_CHROME_ARGS="--no-sandbox --disable-gpu --disable-dev-shm-usage --window-size=1500,1400" \
    KARATE_DRIVER_HEADLESS=true
ENTRYPOINT ["java", "-jar", "/opt/karate.jar"]
