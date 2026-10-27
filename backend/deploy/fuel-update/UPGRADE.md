# RetailZim fuel update + Java 17 → Java 21

This is an **update for an existing RetailZim database at V51–V55**, not a fresh installation. The archive contains the complete shared backend so existing retail/gas routes remain available, with fuel fixes and Java 21 support. No mobile APK or public PHP website deployment is included.

## What this update changes

- Adds the fuel module through existing migrations V52–V55 when the current database is at V51; a V55 database needs no new migrations.
- Preserves historical migrations V1–V51 exactly from the original release source, allowing Flyway to verify their original checksums.
- Compiles the backend for Java 21. Java 17 remains installed for rollback; only this application's service moves to Java 21.
- Corrects fuel delivery/shift timestamp precision, mixed-collation fuel financial reports and HTTP status codes for forbidden fuel cashier operations.
- Does not ship the separate V56 account-disable or V57 fresh-install repair migrations. Existing administrators, passwords, tenants and retail/gas data are retained. Production demo seeding is disabled so restarting the application does not create new demo records.

## 1. Prepare without stopping the existing service

Use a maintenance window with no open fuel shifts or offline cashier queues waiting to sync. Record the current service unit, Java path, application JAR, database URL/name and upload location. Keep the current JAR and service configuration intact. Install a Java 21 JDK alongside Java 17; do not replace the server-wide Java default.

Extract this archive into a separate versioned directory under the existing application directory. Verify `SHA256SUMS` from inside the extracted directory using `sha256sum -c SHA256SUMS` (or the host's equivalent).

Create a root-readable environment file outside the archive using fuel-update.env.example. Copy the actual existing database, JWT, SMTP, payment and application URL values. Preserve the current JWT secret to retain compatibility with existing mobile tokens. Keep any existing SPRING_CONFIG_ADDITIONAL_LOCATION, JAVA_TOOL_OPTIONS and upload/release directory overrides. If the old ExecStart includes JVM flags such as -Xmx, retain them through JAVA_TOOL_OPTIONS when changing the launch command. Match PORT and SERVER_ADDRESS to the current proxy topology. The launcher keeps the service's current working directory, so relative upload paths remain stable.

Secrets are intentionally absent from this JAR. Resolve the placeholders before switching services. Protect the environment file with restrictive permissions (0600). Do not rotate unrelated credentials in the same fuel update unless separately planned.

## 2. Run the read-only server preflight

With Java 21 selected through JAVA_HOME and the existing DB_URL, DB_USERNAME and DB_PASSWORD exported, run:

```sh
python3 preflight.py
```

Python 3 and Java 21 are required on the server; the launcher also repeats preflight automatically before every start.  Dependencies are extracted temporarily from the candidate JAR. It prints the live migration version, validates historical checksums and lists pending fuel migrations. It makes no database changes and performs no repair or baseline.

Proceed only if the final line is `PREFLIGHT PASSED`. A V51 database should list exactly V52–V55. A V55 database should list no pending migrations. Other versions, failed migrations, partial fuel tables and historical checksum differences stop the update. The live version and historical checksums have not been provided in this chat; this check is mandatory on the actual target. Do not bypass it by editing Flyway history.

## 3. Back up and switch during the maintenance window

Stop the existing service and cashier writes. Take a consistent database backup and copy persistent uploads. Preserve the previous JAR, Java 17 service configuration and private runtime configuration. Confirm the backup can be restored using your existing operations procedure.

Update the existing service's JAVA_HOME to Java 21 and its launch command to this directory's start.sh. java21.service.override.example shows a systemd drop-in; adapt the paths and retain the existing WorkingDirectory, service user, permissions, proxy and persistent paths. Do not start a second service on the same port. Start the existing service once with the new command.

Flyway then validates history and applies only pending V52–V55. Hibernate automatic schema changes and automatic baselining are disabled in the production profile. Startup should show Java 21, migration version V55 and Tomcat listening on the existing configured port.

## 4. Accept the update before reopening cashier writes

- Confirm `/actuator/health` reports UP and the normal HTTPS shop login works through the existing proxy.
- Verify an existing retail/gas tenant still has the same users, branch selection and screens.
- Assign the fuel package only to the intended fuel tenant. FUEL_GROWTH is a **fuel-only plan**: selecting it reconciles that tenant's active branches to fuel. Leave existing retail/gas tenants on their current packages. A business that needs multiple modules requires a properly configured mixed-module plan and matching branch/module selections.
- For the fuel tenant, verify dashboard, setup, delivery receiving, pricing, sales, stock, shifts, reports and expenses. Verify cashier access and denial of unauthorized manager operations.
- Open a test shift, receive/verify stock, record the approved test sale and reconcile cash separately from card tenders. Confirm reports in each currency and close the shift with measured meters/dips. Use approved test records; never silently delete real transaction history.
- Reopen cashier writes only after these target-server checks pass.

Fuel measurements and tenders are recorded in the software. Pump/ATG control, payment-provider settlement and fiscal-device integrations remain separate integrations; this update does not certify those external systems.

## Rollback

Keep Java 17 and the previous JAR available. Before reopening writes, an unsuccessful startup can be rolled back by stopping the service and restoring its previous Java 17 launch configuration/JAR. Preserve the database backup; if any new migration is incompatible with the old application, restore the matching database/upload backup while all writes remain stopped. MySQL DDL is not transactionally rolled back by swapping JARs.

After reopening writes, do not restore a pre-update backup without a transaction-recovery plan: that would lose new records. Stop cashier writes, retain the database and uploads, and prefer a reviewed forward fix or a compatible application rollback. Never drop fuel tables or delete successful migration rows on the live system.

## Verification evidence

See RELEASE_RESULTS.md and evidence/. The candidate was tested against isolated upgrade fixtures, not your remote live server. The separate fresh-install migration audit does not describe this release's supported existing-database upgrade path.
