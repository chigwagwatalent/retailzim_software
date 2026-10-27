# Staging the Java 21 backend

Read DEPLOYMENT_READINESS.md first. This bundle is a staging candidate, not production approval.

## Package contents

- retailzw.jar: executable Spring Boot backend
- start.sh: Java 21 and required-environment checks; forces prod profile
- retailzw.service: Linux systemd example
- retailzw-production.env.example: placeholders only
- build.log, test-summary.json, SHA256SUMS: verification evidence

The public PHP website and Flutter applications are separate deployments. Existing mobile APKs have not been certified by this backend package.

## Host setup

Install Java 21 and MySQL matching the target environment. Create a dedicated `retailzw` operating-system user. Put bundle contents under /opt/retailzw and create /opt/retailzw/uploads, /opt/retailzw/uploads/releases and /opt/retailzw/logs owned by that user. Keep application files read-only to the service account where practical.

Create /etc/retailzw/retailzw.env from the example; restrict it to root (0600). Supply staging credentials and HTTPS URLs. The MySQL account must have the DDL permissions required for migrations and application DML, restricted to this application's database. Configure verified database TLS where the database is remote.

Set JAVA_HOME if Java 21 is not the default. Set RETAILZW_JAR only if the JAR path differs. The environment file is read by systemd; start.sh does not source it. For manual testing, export the variables in the launching shell first.

Verify SHA256SUMS, then install the service unit, run `systemctl daemon-reload`, and start `retailzw`. Inspect `journalctl -u retailzw` and application logs. Do not enable automatic startup until staging acceptance passes.

## HTTPS proxy

The backend listens on 127.0.0.1:8080 by default. Place a TLS-terminating reverse proxy in front of it; forward Host, X-Forwarded-Proto and client address correctly, and overwrite incoming forwarded headers. Block public direct access to port 8080. Support WebSocket/event streaming, configure suitable streaming timeouts and allow the intended upload size. Secure cookies require HTTPS.

Set APP_ALLOWED_ORIGINS to comma-separated exact browser origins (scheme + host + optional port). Native mobile clients do not require permissive CORS. Verify login, role isolation, static assets, live support and upload/download behaviour through the proxy.

## Database upgrades and rollback

Before any release, take a consistent database backup and copy persistent uploads. Rehearse restore and upgrades on a clone, including the checksum caveat in DEPLOYMENT_READINESS.md. Flyway's automatic baseline is disabled. No automatic migration repair is performed.

A previous JAR alone is not a database rollback. If schema/data changes are incompatible, stop writes and restore the verified database/upload backup together with the previous artifact, or use a reviewed forward fix. Do not rerun old migrations manually against production.

## Secrets and accounts

Rotate credentials that were previously embedded in repository configuration, and replace demo/default accounts. Do not put live .env files or signing keys in the bundle or Git. Production startup does not create a demo administrator; migration V56 disables the original default bootstrap account. Administrator provisioning and production secret storage are operator release gates.
