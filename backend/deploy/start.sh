#!/bin/sh
set -eu
: "${DB_URL:?Set DB_URL}"
: "${DB_USERNAME:?Set DB_USERNAME}"
: "${DB_PASSWORD:?Set DB_PASSWORD}"
: "${JWT_SECRET:?Set JWT_SECRET}"
: "${APP_BASE_URL:?Set APP_BASE_URL}"
: "${APP_ALLOWED_ORIGINS:?Set APP_ALLOWED_ORIGINS}"
: "${MAIL_HOST:?Set MAIL_HOST}"
: "${MAIL_USERNAME:?Set MAIL_USERNAME}"
: "${MAIL_PASSWORD:?Set MAIL_PASSWORD}"
: "${SMILEPAY_API_KEY:?Set SMILEPAY_API_KEY}"
: "${SMILEPAY_API_SECRET:?Set SMILEPAY_API_SECRET}"
[ "${#JWT_SECRET}" -ge 64 ] || { echo 'JWT_SECRET must contain at least 64 characters' >&2; exit 1; }
case "$APP_BASE_URL" in https://*) ;; *) echo 'APP_BASE_URL must use HTTPS' >&2; exit 1;; esac
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
version=$("$JAVA_BIN" -version 2>&1 | head -n 1)
case "$version" in *'version "21.'*) ;; *) echo 'Java 21 is required' >&2; exit 1;; esac
export SPRING_PROFILES_ACTIVE=prod
exec "$JAVA_BIN" -jar "${RETAILZW_JAR:-./retailzw.jar}"
