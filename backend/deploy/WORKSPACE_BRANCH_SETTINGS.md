# Branch workspace and administrator settings

## Behaviour

- Tenant-wide administrators and accountants default to **All branches**. The navbar selection persists for the signed-in user's session and returns to the same supported page. A stale/deactivated selection resets to All branches.
- Tenant administrators and accountants can switch between their tenant's branches even when their profile has an assigned branch. Supervisors and cashiers remain branch-restricted.
- All-branches operational pages are read-only, paginated activity tables with branch names. Select a branch to use its existing detailed workspace, stock imports, cash operations, detailed reports and other branch-specific actions.
- Category definitions remain shared across the tenant. Category product counts follow the selected branch; the directory is now a single table with the existing edit/status actions.
- Shared shop records (such as suppliers and customer accounts) remain tenant-wide.
- The new all-branches queries use tenant-filtered scalar projections and database pagination, capped at 100 rows per page. This is not a claim that every legacy report has been load-tested or rewritten; existing detailed branch reports still need separate large-data load testing.

## Platform settings

`/admin/settings` provides profile editing, current-password-verified password changes, paginated administrator listing, creation, profile editing, optional password reset and activation/deactivation. These are platform-wide administrators, not tenant staff accounts.

Passwords use the existing password encoder; CSRF protection remains enabled. Status changes are serialized and recheck the acting administrator. Self-deactivation is rejected. Disabled accounts and sessions whose recorded password version changes are rejected on subsequent administrator requests. Existing live streams expire/reconnect according to the support stream timeout.

## Verification

- `mvn test package`: standard unit/contract checks and executable backend package.
- Optional local database smoke test: `mvn -Dretailzw.localSmoke=true -Dtest=WorkspaceLocalSmokeTest test`. Uses the configured local database, disables seed execution, schema updates, migrations and billing workers; request transactions roll back. Renders tenant all-branches and selected-branch pages, platform Settings and checks the gas activity projection queries.
- `tool/test-workspace-pages.cjs` checks browser rendering of the real Thymeleaf smoke-test output. Font Awesome resources must be extracted into `target/workspace-smoke/assets` for this offline visual check.

No production deployment, remote Git push, shop deletion or credential reset is part of this change.
