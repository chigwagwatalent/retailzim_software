# Live support and shop deletion

This change adds authenticated SSE snapshot notifications on admin and tenant pages,
live chat unread badges, bounded 30-shop inbox search/pagination, grouped unread queries,
explicit POST read receipts, and idempotent message submission (4,000-character limit).
V50 adds unread/cursor indexes and the retry reference. Apply through Flyway before serving
the new application. Database snapshots run every 2.5 seconds while clients are connected;
this is not a sub-second message broker. Multiple instances see the same committed database.
Authenticated shop support remains accessible when billing locks other shop features.

Configure the reverse proxy to disable buffering for `/admin/live/events` and
`/shop/live/events`, allow text/event-stream and a read timeout above 60 seconds.
Keep HTTPS, same-origin session cookies and CSRF enabled. SSE sessions expire every
45 seconds and reconnect through authentication. Hidden tabs disconnect. Connections
are capped at 1,000 per application instance and eight per shop user; platform streams
are capped at 100. Capacity above these bounds requires measurement and infrastructure
work, not simply raising limits. Snapshot polling and writes still consume database capacity.

## Destructive feature

Only platform administrators can POST `/admin/tenants/{id}/delete`. The shop must already
be suspended/cancelled; the administrator must type `DELETE <exact shop code>` and check
the acknowledgement. Stop tills, drain in-flight requests/background payment processing,
and take a verified backup before using this feature. It permanently deletes all listed
tenant-owned database tables and explicit child rows in one transaction, leaving FK checks
enabled. Failure rolls the transaction back. Large deletions have a 120-second timeout;
schedule maintenance for large tenants rather than repeatedly retrying under load.

It does NOT erase backups, exports, external payment-provider records, device-local copies,
shared platform announcements, shared software releases, or operational logs. The warning
is visible in the UI. Deleting paid billing history changes historical platform reporting.
Review retention obligations before using this feature. No production shop has been deleted
as part of implementation or testing.

## Required staging acceptance

- Apply migrations against a disposable MySQL copy. Use two shops and both business modules.
- Sign in as platform admin and both tenants: test simultaneous messages, unread icons while
  widgets are closed, reopening, read receipts, reconnect and retry after a dropped response.
- Confirm tenant A cannot read tenant B messages and shop users cannot call admin endpoints.
- Test wrong CSRF, expired sessions and slow/disconnected clients through the real proxy.
- Delete only a disposable suspended tenant with every record type populated; check no orphan
  tenant/child rows and verify the other tenant and global plans are unchanged.
- Run realistic multi-instance/load tests with database metrics and background payment work.

Unit tests and static browser checks do not replace the MySQL/proxy/load acceptance above.
