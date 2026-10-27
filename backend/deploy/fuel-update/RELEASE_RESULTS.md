# Fuel module update 1.0.1-fuel-java21 — 22 October 2026

**Status: packaged and tested for an existing-system V51–V55 upgrade. Run the included read-only preflight on the actual server before switching its Java 17 service to this Java 21 release.**

## Included

- Complete shared Java 21 backend with existing retail/gas routes and the fuel management module.
- Original historical migrations V1–V51; fuel-only pending migrations V52–V55. No V56/V57 account/fresh-install changes.
- Fuel fixes: microsecond shift opening/closing times; collation-independent currency aggregation in fuel reports; HTTP 403 for unauthorized fuel API roles.
- Production demo seeding disabled. The release requires external current credentials; it preserves the existing JWT secret and accounts rather than resetting them.
- Java 21 launcher with automatic read-only preflight and migration target fixed at V55; production Hibernate DDL updates disabled.

## Verification

| Check | Result |
| --- | --- |
| Java 21 backend regression suite | 146 passed; 8 opt-in checks skipped in the default run |
| Opt-in existing-database regression/upgrade tests | 8 passed, none skipped: 2 existing page/branch checks, 4 transaction/schema checks, 2 fuel entitlement/page/reconciliation checks |
| V51 → V55 migration rehearsal | Passed; exactly V52, V53, V54 and V55 applied |
| Existing V55 deployment rehearsal | Passed; no migrations pending/applied |
| Preflight negative check | An intentional historical checksum mismatch was rejected |
| Account preservation | Administrator/user identities, password hashes and enabled status unchanged by both upgrade rehearsals |
| Production artifact startup | Exact packaged JAR starts with the prod profile on Java 21; login HTTP 200 and health HTTP 200/UP |
| Runtime protection | Launcher refuses Java 17; application classes target Java 21 (class major version 65) |
| Fuel management access | Dashboard, setup, deliveries, pricing, shifts, reports, sales, stock and expenses render with actual fuel package entitlement |
| Cashier permissions | Cashier bootstrap allowed; manager-only setup denied; admin use of cashier-only fuel POS API returns 403 |
| Fuel transactions | Delivery, effective price, mixed CASH/CARD sale, idempotent replay, stock deduction, meter/dip closure, cash variance and expense/report totals verified |
| Existing retail/gas regression | Retail sale/retry, cash closure, return/restock, purchase approval/receiving and gas sale/retry/shift closure verified |

The collation regression was reproduced on the restored schema and fixed without altering historical migrations. Fuel tests now reconcile a USD 10 sale containing USD 4 cash and USD 6 card against a USD 5 opening float: expected cash USD 9, with zero cash, meter and wet-stock variances.

## Fixture provenance and limits

The remote live server has not been accessed. The live migration version was not supplied, so server preflight remains a required installation step.

Rehearsals used disposable copies of the local existing database, preserving its Hibernate-evolved core schema. One fixture had only fuel tables/history removed to model the pre-fuel V51 state. Its historical checksums were normalized **only in the disposable fixture** to the original released SQL from Git baseline d1624e402ba9fcda66895d1b96209a5f18eaeccd. This is a controlled test fixture, not proof of an unknown remote history. The production preflight never repairs/baselines history and must pass against the actual live database.

Tests used real database services, package entitlement and HTTP security for fuel management. Payment/email background workers were mocked in integration tests. During packaged startup, their initial delays were extended and billing reminders disabled solely in the temporary test environment; production retains existing operational scheduling.

Retail/Gas/Fuel mobile suites were previously checked in this workspace (30/16/4 tests and clean analysis), but no APK is included in this backend update. Existing cashier apps and the public PHP website are retained separately. Physical pumps, ATG controllers, fiscal devices, provider settlement and hardware printers were not exercised by this backend upgrade rehearsal.

The earlier fresh-install audit is outside this existing-system release scope; it remains recorded separately. Do not use this package to bootstrap an empty database.

## Build provenance

The isolated build script uses the original Git baseline and overlays only the Java 21 POM, Java 21 date-label compatibility, production-seeding guard, fuel service/controller fixes and opt-in upgrade tests. It sanitizes embedded credentials into environment placeholders and disables production automatic schema updates/baselining. Other in-progress working-tree changes, the broad staging hardening experiment, mobile edits, and fresh-install migrations are excluded.

See build-manifest.json, evidence/ and SHA256SUMS for the exact artifact and test evidence. Follow UPGRADE.md for the server switch and rollback procedure.
