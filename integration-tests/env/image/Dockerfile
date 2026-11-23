# Magnolia CE sandbox image: official Tomcat image + Magnolia CE webapp WAR from Magnolia's public Nexus.
#
#   6.3.x -> BASE_IMAGE=tomcat:9.0-jdk17-temurin   (javax, Servlet 4)
#   6.4.x -> BASE_IMAGE=tomcat:10.1-jdk17-temurin  (Jakarta EE 10, Servlet 6)
#
# MGNL_FLAVOR:
#   ce   -> info.magnolia.bundle:magnolia-community-webapp:<ver>:war      (plain CE, ~190 MB)
#   demo -> info.magnolia.demo:travel-demo-community-webapp:<ver>:war     (CE + travel demo)
ARG BASE_IMAGE=tomcat:10.1-jdk17-temurin
FROM ${BASE_IMAGE}

ARG MGNL_VERSION
ARG MGNL_FLAVOR=ce
ARG NEXUS=https://nexus.magnolia-cms.com/repository/public

ENV MGNL_VERSION=${MGNL_VERSION} \
    MGNL_FLAVOR=${MGNL_FLAVOR} \
    MGNL_WEBAPP_DIR=/usr/local/tomcat/webapps/magnoliaAuthor

RUN set -eux; \
    test -n "${MGNL_VERSION}"; \
    case "${MGNL_FLAVOR}" in \
      ce)   GP=info/magnolia/bundle; A=magnolia-community-webapp ;; \
      demo) GP=info/magnolia/demo;   A=travel-demo-community-webapp ;; \
      *) echo "unknown MGNL_FLAVOR ${MGNL_FLAVOR}"; exit 1 ;; \
    esac; \
    URL="${NEXUS}/${GP}/${A}/${MGNL_VERSION}/${A}-${MGNL_VERSION}.war"; \
    echo "Downloading ${URL}"; \
    curl -fsSL -o /tmp/mgnl.war "${URL}"; \
    echo "$(curl -fsSL "${URL}.sha1" | cut -c1-40)  /tmp/mgnl.war" | sha1sum -c -; \
    rm -rf /usr/local/tomcat/webapps/*; \
    mkdir -p "${MGNL_WEBAPP_DIR}"; \
    cd "${MGNL_WEBAPP_DIR}" && jar xf /tmp/mgnl.war; \
    rm -f /tmp/mgnl.war; \
    echo "${A}:${MGNL_VERSION}" > /usr/local/tomcat/MAGNOLIA_ARTIFACT; \
    mkdir -p /data; \
    chmod -R a+rwX /usr/local/tomcat /data

COPY log4j2-loggers.xml log4j2-appenders.xml mgnl-entrypoint.sh /opt/mgnl/

# Build a sandbox log4j2 config = default config + dedicated update/delta-task log (magnolia-update.log)
RUN set -eux; \
    D="${MGNL_WEBAPP_DIR}/WEB-INF/config/default"; \
    awk -v L=/opt/mgnl/log4j2-loggers.xml -v A=/opt/mgnl/log4j2-appenders.xml ' \
      /<Appenders>/ {print; while ((getline line < A) > 0) print line; next} \
      /<Loggers>/   {print; while ((getline line < L) > 0) print line; next} \
      {print}' "$D/log4j2.xml" > "$D/log4j2-sandbox.xml"; \
    grep -q log-update "$D/log4j2-sandbox.xml"; \
    chmod a+rx /opt/mgnl/mgnl-entrypoint.sh; chmod -R a+rwX "$D"

VOLUME /data
EXPOSE 8080
ENTRYPOINT ["/opt/mgnl/mgnl-entrypoint.sh"]
CMD ["catalina.sh", "run"]
