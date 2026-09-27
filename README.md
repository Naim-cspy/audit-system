# Supermarket POS & Financial Audit System (Multi-Tenant SaaS)

An enterprise-grade, multi-tenant Android application for Supermarket Point of Sale (POS), Inventory Auditing, Financial Intelligence, and Cloud Synchronization, built with **Kotlin**, **Jetpack Compose**, **Room SQLite**, and **Google Cloud Firestore / Firebase**.

---

## 1. System Architecture

```text
                               ┌──────────────────────────────────────────────────┐
                               │            SaaS Platform Owner Console            │
                               │      (Access restricted to isPlatformAdmin)      │
                               └─────────────────────────┬────────────────────────┘
                                                         │
                                    /platform_analytics/global_summary
                                                         │
               ┌─────────────────────────────────────────┴────────────────────────────────────────┐
               │                                                                                  │
  ┌────────────▼──────────────┐                                                      ┌────────────▼──────────────┐
  │   Tenant Store Alpha      │                                                      │   Tenant Store Beta       │
  │   /stores/STR-001/        │                                                      │   /stores/STR-002/        │
  ├───────────────────────────┤                                                      ├───────────────────────────┤
  │ - products/               │                                                      │ - products/               │
  │ - sales/ (immutable)      │                                                      │ - sales/ (immutable)      │
  │ - ledger/ (immutable)     │                                                      │ - ledger/ (immutable)     │
  │ - auditLogs/ (append-only)│                                                      │ - auditLogs/ (append-only)│
  │ - syncEvents/             │                                                      │ - syncEvents/             │
  │ - users/                  │                                                      │ - users/                  │
  └───────────────────────────┘                                                      └───────────────────────────┘
```

### Strict Tenant Isolation
- **Firestore Partitioning**: All tenant assets reside strictly under `/stores/{storeId}/...`.
- **Security Rules Enforcement**: Rules enforce `isStoreMember(storeId)` using verified token claims (`request.auth.token.store_id == storeId`). Cross-tenant read/write attempts immediately return `PERMISSION_DENIED`.
- **Platform Analytics Boundary**: Global metrics (`/platform_analytics`) and cross-store security logs (`/security_events`) are accessible solely to verified SaaS platform owners (`isPlatformAdmin()`). Client store tokens cannot read or modify platform telemetry even if bypassing the Android UI.

---

## 2. Authentication & Credential Governance

- **Zero Hardcoded Production Credentials**: All pre-baked default credentials have been removed. 
- **First-Time Administrator Setup**: On initial launch on a fresh terminal, the application displays a secure first-time setup flow to initialize the store administrator with salted PBKDF2 encryption.
- **Firebase Auth with Verified Custom Claims**: Production accounts authenticate via Firebase Authentication. Authorization is governed by trusted custom token claims issued by the Firebase Admin SDK:
  - `store_id`: Binds the user to their specific store tenant.
  - `role`: One of `OWNER`, `ADMIN`, `MANAGER`, `CASHIER`, `ACCOUNTANT`.
  - `platform_admin`: Boolean flag granting SaaS Command Center access (assigned exclusively to the SaaS Platform Owner).
- **Offline Cryptographic Security**: Local credentials are encrypted using PBKDF2 with unique cryptographic salts (`PasswordSecurity.kt`). Offline logins strictly evaluate against the local store cache and never grant SaaS Platform Owner privileges.

---

## 3. Server-Side Provisioning & Metrics Engine

Administrative and provisioning scripts reside in `/server_provisioning/` and run securely using the Firebase Admin SDK:

```bash
# Install server dependencies
cd server_provisioning
npm install

# 1. Provision SaaS Platform Owner
export GOOGLE_APPLICATION_CREDENTIALS="/path/to/serviceAccountKey.json"
node provision_saas_owner.js --email Zawaruldo69@gmail.com --displayName "SaaS Owner"

# 2. Provision Tenant Store & Store Owner
node provision_tenant_store.js --storeId STR-001 --storeName "Al-Makhzen Supermarket" --ownerEmail owner@almakhzen.com --ownerPassword "SecurePass123!"

# 3. Create or Manage Store Employees
node manage_store_employee.js create --storeId STR-001 --email cashier@almakhzen.com --password "CashierPass123!" --role CASHIER
node manage_store_employee.js disable --storeId STR-001 --email cashier@almakhzen.com

# 4. Authoritative Platform Business Metrics Aggregation
node aggregate_platform_metrics.js

# 5. Google Analytics 4 Server-Side Reporting Pipeline
export GA4_PROPERTY_ID="your_ga4_property_id"
node fetch_ga4_reporting.js

# 6. Run Firestore Rules Security Test Suite
npm run test-rules
```

---

## 4. Truthful Cloud Synchronization

- **Tenant Isolation**: Only records matching the authenticated `storeId` are ever read or written.
- **Atomic Local Transactions**: Checkout decrements stock, records sales rows, appends general ledger balance, and writes audit logs inside a single SQLite transaction (`runInTransaction`).
- **Idempotent Retries**: Sales and ledger documents are committed to immutable Firestore paths using stable deterministic keys. Retries reconcile against existing cloud records without duplicating rows.
- **Full Bidirectional Sync**: Replicates Products, Sales, and Financial Ledger entries in both directions between SQLite/Room and Cloud Firestore.
- **Durable Deletion Queue**: Product deletions are recorded in `pending_sync_deletions` in Room so deletions survive app restarts, network outages, and terminal reboots.
- **Conflict Handling**: Remote product updates use last-write-wins based on `updated_at` timestamps while protecting uncommitted local changes and pending deletions.
- **Accurate Metric Reporting**: Partial syncs record a `PARTIAL_SUCCESS` event with failure breakdowns. The successful sync timestamp advances only when all operations succeed without error.

---

## 5. Google Analytics 4 Telemetry & Separation of Metrics

- **Real-Time Client Telemetry**: Application actions (logins, navigation, POS purchases with documented items array, inventory edits, security alerts) stream in real time via the Firebase Analytics SDK. Verifiable in Firebase Analytics DebugView.
- **Strict Separation of Concerns**: Authoritative business metrics (revenue, transactions, stores, stock levels) are computed by the backend aggregation pipeline (`aggregate_platform_metrics.js`). Google Analytics reporting (sessions, feature usage) is queried server-side via the GA4 Reporting API (`fetch_ga4_reporting.js`).
- **No Fabricated Fallbacks**: When GA4 reporting infrastructure is unconfigured, the dashboard displays "Not configured" rather than synthetic or hardcoded numbers.

---

## 6. Verification Suite & Diagnostics

- **Production-Safe Diagnostics**: Production diagnostics in the Admin Center are strictly read-only. Unsafe fixed write tests (`TEST001`) have been replaced with read-only connectivity checks and isolated emulator test fixtures.
- **Evidence-Based Reporting**: Checks only pass if they actually execute and succeed against the runtime Firebase project and Android package (`com.aistudio.supermarketpos.audit`).
- **Multi-Phase E2E Checks**:
  1. Phase 1: Firebase Initialization
  2. Phase 2: Project ID & App ID Verification
  3. Phase 4: Authentication State
  4. Phase 7: Verified Custom Token Claims
  5. Phase 8: Read-Only Firestore Connectivity
  6. Phase 13: Tenant Isolation Verification (requires `PERMISSION_DENIED` on foreign store)
  7. Phase 14: Platform Metrics Authorization Barrier (verifies platform admin access / tenant denial)
  8. Phase 19: Client Secret Audit (confirms zero embedded private keys)
