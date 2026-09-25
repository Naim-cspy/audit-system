# Audit System — Cloud Visibility & Multi-Tenant Analytics Architecture

## 1. Firestore Data Hierarchy

Every store operational record is strictly compartmentalized under a root tenant document:

```
stores/{storeId}/
  ├── products/{productId}       # Store inventory, barcode, pricing, stock levels
  ├── sales/{saleId}             # Atomic checkout records, items sold, timestamp
  ├── ledger/{ledgerId}          # Financial ledger (money in/out, expenses, revenues)
  ├── users/{userId}             # Store cashiers, managers, administrators
  ├── auditLogs/{logId}          # Append-only operational audit trail
  └── syncEvents/{syncId}        # Device synchronization state & diagnostics

admin_access_logs/{logId}        # Platform admin investigation audit trail (append-only)
security_events/{eventId}        # Failed logins, cross-store attempts, rate-limits
```

---

## 2. Tenant Isolation & Security Rules

All queries are validated server-side by Firebase Security Rules:

```javascript
function isStoreMember(storeId) {
  return request.auth != null && request.auth.token.store_id == storeId;
}
```

- **Cross-Tenant Prevention**: If an employee from `Store A` attempts to read `stores/StoreB/products` or `stores/StoreB/sales`, the request is immediately rejected with `PERMISSION_DENIED` at the Firestore security boundary.
- **Client Trust Zero**: Store ID is extracted directly from the verified cryptographic JWT `request.auth.token.store_id`, never from untrusted client payload bodies.
- **Immutable Financials**: `sales`, `ledger`, and `auditLogs` disallow client-side `update` and `delete` operations (`allow update, delete: if false`).

---

## 3. Platform Admin & Support Tooling

Platform administrators operate through a separate, dedicated web interface with `platform_admin == true` claims:

- **Default Read-Only**: Support agents inspect store operational collections in read-only mode to prevent accidental data modification during troubleshooting calls.
- **Traceable Investigations**: Every admin lookup on customer data records an entry in `admin_access_logs`:
  - `admin_id`: ID of the platform employee
  - `store_id`: Target supermarket ID
  - `timestamp`: UTC ISO-8601 timestamp
  - `action`: `VIEW_SALES`, `VIEW_INVENTORY`, `EXPORT_BACKUP`, `SUPPORT_INVESTIGATION`
  - `reason`: Customer ticket ID or reason for access

---

## 4. Customer Support Investigation Workflow

When a store owner reports: *"Yesterday's sale disappeared"*, the support workflow follows a deterministic state machine:

1. Query `stores/{storeId}/syncEvents` by date or transaction ID.
2. Determine event status:
   - `SUCCESS`: Transaction was committed and replicated to cloud storage.
   - `PENDING`: Transaction queued in device local Room cache awaiting connectivity.
   - `REJECTED`: Transaction rejected by server rules (e.g. Insufficient stock or expired session).
   - `DUPLICATE`: Idempotent retry detected and safely deduplicated.
   - `FAILED_SYNC`: Network transport error or timeout (logged with `error_code` and `error_message`).
3. Support provides verifiable transaction records and audit logs directly to the customer.

---

## 5. Aggregated Regional Demographics & Purchasing Analytics

To benchmark purchasing power and inventory turnover without exposing private store business data:

- **Aggregation Domain**: Data is combined across geographical zones (e.g. `Nabatieh Area, South Lebanon`).
- **Metrics Computed**:
  - Category demand distribution (% share of regional volume)
  - Average transaction basket size
  - Velocity indicators: Fast-moving items vs Slow-moving inventory
- **Zero Exposure**: No individual store names, prices, or margins are revealed to competitors.
- **Privacy Standard**: Customer operational records are never sold, traded, or shared with third-party advertising brokers.
