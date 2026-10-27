#!/bin/sh
set -eu
: "${DB_URL:?Set the existing database JDBC URL}"
: "${DB_USERNAME:?Set the existing database username}"
: "${DB_PASSWORD:?Set the existing database password}"
: "${JWT_SECRET:?Preserve the current JWT secret during the upgrade}"
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
case "$("$JAVA_BIN" -version 2>&1 | head -n 1)" in *'version "21.'*) ;; *) echo 'This update requires Java 21; point JAVA_HOME at Java 21.' >&2; exit 1;; esac
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
python3 "$SCRIPT_DIR/preflight.py"
export SPRING_PROFILES_ACTIVE=prod
exec "$JAVA_BIN" -jar "$SCRIPT_DIR/retailzw-fuel-java21.jar" \
  --spring.flyway.enabled=true \
  --spring.flyway.locations=classpath:db/migration \
  --spring.flyway.target=55 \
  --spring.flyway.baseline-on-migrate=false \
  --spring.jpa.hibernate.ddl-auto=none
