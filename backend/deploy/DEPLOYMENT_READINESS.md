# RetailZim deployment readiness — 22 October 2026

**Decision: production release on hold. The 22 October deeper audit found fresh-install schema failures; the staging candidate is not release approved.**

See [TEST_RESULTS_20261022.md](TEST_RESULTS_20261022.md) for new database, transaction and mobile verification, including the blocking missing-column inventory.
The existence of a screen, service, APK or passing unit test does not certify its complete business workflow.

## Module assessment

| Area | Evidence | Outstanding release gate |
| --- | --- | --- |
| Retail sales, inventory, purchasing, returns, cash and credit/change | Service, controller and contract tests are present and run in the backend suite | Authenticated create-sale → payment → stock → return → cash reconciliation on a staging database |
| Customers, suppliers, branches, users and roles | Implemented web/API routes; branch/role tests | Cross-tenant and role matrix using real staging accounts; verify every write workflow |
| Reports, accounting, currency and wholesale pricing | Report, accounting, currency and pricing tests | Reconcile sample ledgers to independently calculated currency totals; complete journal/costing scope is not certified |
| Gas | 13 gas service tests, supervisor and tank-related source | Delivery → dispensing → tank balance → shift close on real staging data and GasPOS devices |
| Fuel | 7 fuel service tests; tanks, pumps, deliveries, shifts, sales, expenses implemented | Pump/ATG hardware integration and fiscal integration incomplete; fiscal records are queued. See FUEL_RELEASE_READINESS.md |
| Billing, subscriptions and SmilePay | Billing and checkout tests; provider responses simulated locally | Provider sandbox initiation, signed webhook, duplicate/replayed webhook and settlement reconciliation |
| Support, notifications and email | Support safety tests; implemented services | SMTP delivery and authenticated concurrent chat/event-stream exercise |
| Retail POS, GasPOS and FuelPOS apps | Source and tests exist; existing binaries are not rebuilt here | Flutter tests and analysis now pass using the installed SDK; signing checks, full offline recovery/sync, printers and real-device acceptance remain |
| Public website | Separate PHP application in frontwebsite | Separate PHP/database/runtime and deployment verification; excluded from backend bundle |

## Verification performed

- Java 21 compile and Maven verify; see bundled test-summary.json and build.log for final results.
- Default suite contains 153 tests: 146 passed and 7 opt-in checks skipped. The opt-in database checks were also run separately; see the test-results report for passes and known fresh-install failures.
- Shell syntax and required-environment failure checked for deployment launcher.
- Fresh and cloned MySQL migrations and authenticated MockMvc page checks have now run. Fresh-install transaction and schema checks found blockers. External provider, browser/device and hardware certification remain unverified.

## Changes for deployment

- Environment-provided DB, SMTP, JWT and payment credentials; no live credentials in YAML defaults.
- Production demo seeding disabled; secure HTTP-only session cookies and explicit allowed browser origins.
- Production schema managed by Flyway; Hibernate automatic updates disabled. Baseline is not automatic.
- V56 disables only the original V1 superadmin bootstrap password hash. Custom passwords are preserved. Before release, provision a protected named administrator and disable demo users/tenants; rotate all formerly embedded secrets and known default credentials, including any existing `platform` account.
- Java 21 launcher, Linux service definition, environment example and checksummed executable JAR.

## Mandatory staging acceptance

1. Use a separate MySQL staging database. Restore a backup of the deployment target into it; never point staging at production. Also exercise a fresh installation.
2. Validate Flyway history against this candidate. Earlier work changed V44, V45, V47, V50 and V51: a target that already applied older checksums may reject them. Do not blindly repair history, delete success rows or baseline a populated database. Review each DDL difference and use a deliberate forward-only upgrade before release.
3. Run the candidate with the prod profile and real staging environment settings; inspect migrations through V57. With Hibernate updates disabled, missing schema must be corrected through reviewed migrations, not by enabling `ddl-auto=update`.
4. Establish a named administrator with a unique strong password and verify login before rollout. V56 intentionally disables the original bootstrap credential; a fresh installation needs deliberate administrator provisioning.
5. Run the authenticated workflows listed above. With an appropriately seeded disposable database, opt-in page checks can run with Maven `-Dretailzw.localSmoke=true -Dtest=WorkspaceLocalSmokeTest test`; these tests require an active accountant and branch fixture, mock background services and do not replace transaction acceptance.
6. Complete app/device, provider and hardware gates relevant to the modules being sold. Fuel cannot be described as hardware-controlled or fiscally certified by this build.
7. Test backup restore and target-volume/concurrent-cashier behaviour. Resolve findings before production approval.

No remote service was changed, no production database was contacted and no mobile release was published in this staging task.
