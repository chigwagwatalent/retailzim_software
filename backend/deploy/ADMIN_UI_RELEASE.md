# Admin UI and reporting update

Includes shared platform styling, an in-flow footer on all ten admin templates,
keyboard-scrollable tables, real six-month registration aggregation, correctly
counted active plans, and deterministic five-row recent-shop ordering.

Subscription summaries now aggregate active shops by plan and count upcoming
renewals in SQL instead of loading all tenant entities. Migration V49 adds
registration-date and renewal-date indexes. Existing shop changes and V48 remain
in this build.

Validation: 115 unit/contract tests passed. Source-template browser fixtures checked
at 1440 and 390 pixels; all desktop layouts passed overflow checks. Release management
still has a horizontal-overflow issue at 390 pixels. Fixtures are not authenticated
end-to-end tests and do not evaluate every Thymeleaf condition or production query.

Before deployment:
- Back up the database and run V48/V49 on a representative staging copy. Measure
  index creation duration/locking and inspect query plans with MySQL EXPLAIN.
- Smoke-test authenticated admin navigation, tenant mutations, billing actions,
  release uploads, table actions, dark mode and logout.
- Load-test concurrent admin and POS traffic together. Record p50/p95/p99 latency,
  SQL timings, connection-pool wait, heap and CPU. Test growing tenant/sale volumes.
- Tune pool limits across all application instances to fit the database capacity;
  do not assume increasing the pool automatically improves throughput.
- Review remaining large-list endpoints (including release and support histories)
  before claiming complete platform scalability. No production load-capacity claim
  or deployment is made by this update.

Health labels deliberately distinguish unavailable monitoring from confirmed
health. API and payment-provider health require a real monitoring integration.
