#!/bin/bash
# Wraps env/common/mgnl-entrypoint.sh: installs the project jars (env/jars) in author + public (same Tomcat),
# clones the webapp to magnoliaPublic, writes the public overlay (/data/public) and the author receiver (/data/mp).
set -euo pipefail
A=/usr/local/tomcat/webapps/magnoliaAuthor; P=/usr/local/tomcat/webapps/magnoliaPublic
cp /lvenv/jars/*.jar "$A/WEB-INF/lib/"
[ -d "$P" ] || cp -a "$A" "$P"
cp /lvenv/jars/*.jar "$P/WEB-INF/lib/"
mkdir -p /data/public/{repositories,logs,cache,tmp,history,light-modules,keys} /data/mp
cat > "$P/WEB-INF/config/magnoliaPublic/magnolia.properties" <<EOP
magnolia.bootstrap.authorInstance=false
magnolia.bootstrap.dir=WEB-INF/bootstrap/public WEB-INF/bootstrap/common
magnolia.repositories.home=/data/public/repositories
magnolia.repositories.jackrabbit.config=WEB-INF/config/repo-conf/jackrabbit-bundle-h2-search.xml
magnolia.logs.dir=/data/public/logs
magnolia.cache.startdir=/data/public/cache
magnolia.upload.tmpdir=/data/public/tmp
magnolia.exchange.history=/data/public/history
magnolia.resources.dir=/data/public/light-modules
magnolia.author.key.location=/data/public/keys/magnolia-activation-keypair.properties
magnolia.password.manager.key.location=/data/public/keys/magnolia-password-manager-keypair.properties
magnolia.update.auto=true
magnolia.develop=false
magnolia.bootstrap.samples=true
log4j.config=WEB-INF/config/default/log4j2.xml
magnolia.superuser.bootstrap.password=${MGNL_SUPERUSER_PASSWORD:-superuser}
EOP
cat > /data/mp/microprofile-config.properties <<EOP
magnolia.publishing.receivers[0].name=magnoliaPublic
magnolia.publishing.receivers[0].url=http://localhost:8080/magnoliaPublic
magnolia.publishing.receivers[0].enabled=true
EOP
exec /opt/mgnl/mgnl-entrypoint.sh "$@"
