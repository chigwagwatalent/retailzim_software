# RetailZim verification — 22 October 2026

**Release decision: HOLD. Local testing is materially broader now, but not every module is production-certified.**

## Results

| Check | Result | Scope |
| --- | --- | --- |
| Java 21 backend regression suite | Pass | See audit-20261022/backend-verify.log for exact test totals. Opt-in database checks are reported separately below. |
| Fresh MySQL 8.0.46 migrations | Pass | V1–V56 applied from empty; V57 applied in a subsequent upgrade. Passing SQL does not imply application/schema compatibility. |
| Upgrade and restore rehearsal | Pass on local copy | Consistent dump of retail_zw restored to a separate schema; V55 → V56 → V57 upgrade validated and applied. No production-target restore or rollback drill claimed. |
| Authenticated page/branch checks | 2 tests passed | Existing WorkspaceLocalSmokeTest: 14 tenant sections, branch variants, admin settings and gas activity queries. MockMvc checks, not visual browser/device acceptance. |
| Existing-database workflow checks | 4 tests passed | Retail sale, retry idempotency, stock deduction, cash closure, return/restock, PO submission/approval/receiving; gas tank sale/retry/shift closure; fuel delivery/sale/retry/meter/dip/cash reconciliation; mapped-column presence. |
| Fresh-install transaction checks | FAIL | Gas and fuel transaction tests pass. Retail transaction fails because migrated sales schema lacks coupon_discount; a wider audit found additional missing mapped columns. |
| Fresh-install mapped columns | FAIL | 72 missing columns across 15 tables. Full inventory: audit-20261022/missing-fresh-schema-columns.txt. Existing-database clone passes the same check. |
| Strict Hibernate schema validation | FAIL | First reported type mismatch: borrower_transactions.currency is VARCHAR; entity mapping expects ENUM. Validator stops at the first mismatch, so this is not an exhaustive type audit. |
| Retail POS Flutter | 30 tests passed; analysis clean | Includes offline/API sync and responsive UI tests in the existing suite. |
| GasPOS Flutter | 16 tests passed; analysis clean | Login, app state, tank display, service and model tests. |
| FuelPOS Flutter | 4 tests passed; analysis clean | Login and model tests. This is limited coverage, not end-to-end FuelPOS acceptance. |
| PHP public website | Not run | PHP runtime was not available; excluded from Java backend verification. |
| Real providers, devices and hardware | Not run | No live SMTP delivery, payment settlement, physical printing, Android/iOS install, pump/ATG or fiscal-device session performed. |
| Load/concurrency | Not run | Sequential retry idempotency was tested; simultaneous multi-cashier races and target-volume load were not. |

## Bugs corrected during testing

1. Fuel shift opening/closing and effective-price SQL now uses CURRENT_TIMESTAMP(6), matching the schema precision. A delivery immediately before shift opening could previously be counted as an in-shift delivery, producing a false wet-stock variance. The database regression test initially reproduced a 100-litre false variance and now passes.
2. V57 makes products.max_stock_level optional, matching the product request/model. Fresh schemas previously rejected a product with no maximum stock value, while Hibernate-updated databases accepted it. The failing fresh product-create operation now succeeds; the test proceeds to the separate missing sales-column failure.
3. Retail POS async calls inside try/catch now await completion so asynchronous failures are caught by the intended recovery handlers. Six analyzer warnings were eliminated.
4. FuelPOS form fields use initialValue in place of the deprecated value parameter. Three analyzer notices were eliminated; tests rerun successfully.

## Remaining deployment blockers

- Reconcile the 72 missing columns with the existing legacy schema and models using reviewed forward-only migrations. Include data backfills and legacy-column compatibility; do not mask the defect by restoring production ddl-auto=update.
- Verify enum/VARCHAR and other column types after missing-column repairs. Repeat fresh and upgrade transaction checks and authenticated pages on a Flyway-only installation.
- Perform actual mobile builds/signing and device/offline recovery testing, printer validation, SMTP and payment-provider sandbox acceptance. Existing APKs were not rebuilt or certified by these source tests.
- Fuel pump-control and fiscal integrations documented in FUEL_RELEASE_READINESS.md are incomplete, and testing cannot certify implementations that are absent.
- Complete independent role/tenant/entitlement acceptance, concurrency/load and operator backup/restore drills before approving production.

## Isolation and reproducibility

All database writes were restricted to separate retailzim_audit_* databases; the original retail_zw database was only read for a consistent backup/clone. Transaction tests roll back their fixtures. Both disposable audit databases were removed after the checks; evidence is retained in the audit directory. Payment reconciliation, notification workers and billing automation were mocked to prevent external effects. The transaction suite also mocks package entitlement so transaction persistence can be exercised on temporary module branches; package/role unit tests and page tests remain separate evidence, not real-provider certification.

Added opt-in tests: DatabaseWorkflowIntegrationTest and ProductionSchemaIntegrationTest. Run with a disposable fixture database supplied through SPRING_DATASOURCE_* environment settings. DatabaseWorkflowIntegrationTest rejects databases whose JDBC URL does not contain retailzim_audit_. Use `-Dretailzw.localSmoke=true -Dtest=DatabaseWorkflowIntegrationTest,WorkspaceLocalSmokeTest` for clone workflows; `-Dretailzw.schemaAudit=true -Dtest=ProductionSchemaIntegrationTest` for strict schema validation. External workers remain mocked. The fresh-install schema failure is intentionally retained as a failing release gate.

Evidence logs are saved in audit-20261022/. The rebuilt staging archive contains a RELEASE_ON_HOLD notice. No remote application, live payment provider or production database was modified.
