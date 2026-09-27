# Trusted Account & Store Provisioning Guide

This guide details the exact steps for deploying Firestore security rules, setting up indexes, and executing server-side provisioning with the Firebase Admin SDK.

---

## 1. Architecture & Consistent Hierarchy

All multi-tenant supermarket data is partitioned under `/stores/{storeId}/`:

```
stores/{storeId}/
  ├── products/{productId}       # Inventory items (managed by OWNER, ADMIN, MANAGER, INVENTORY)
  ├── sales/{saleId}             # POS checkout transactions (immutable, append-only)
  ├── ledger/{ledgerId}          # Financial ledger entries (immutable, append-only)
  ├── users/{userId}             # Store employee documents (matching Auth UID)
  ├── auditLogs/{logId}          # Operational audit logs (append-only)
  └── syncEvents/{syncId}        # Diagnostics & replication records

platform_analytics/global_summary # SaaS Platform-wide aggregates (accessible ONLY to platform_admin)
security_events/{eventId}        # Real security events & access violations
```

---

## 2. Server-Side Custom Claims Model

Authentication authority relies on verified JWT custom claims:

- **SaaS Platform Owner**:
  ```json
  {
    "platform_admin": true,
    "role": "SAAS_OWNER"
  }
  ```
- **Tenant Store Owner**:
  ```json
  {
    "store_id": "STR-001",
    "role": "OWNER",
    "platform_admin": false
  }
  ```
- **Tenant Cashier / Manager / Accountant**:
  ```json
  {
    "store_id": "STR-001",
    "role": "CASHIER",
    "platform_admin": false
  }
  ```

---

## 3. Step-by-Step Deployment Instructions

### Step 3.1: Download Firebase Service Account Key
1. Go to the [Firebase Console](https://console.firebase.google.com/).
2. Select your project: `audit-system-e7707`.
3. Navigate to **Project Settings** > **Service accounts**.
4. Click **Generate new private key** and save the JSON file (e.g. `serviceAccountKey.json`).
5. **CRITICAL SECURITY MANDATE**:
   - Store this file on your secure server or workstation.
   - **NEVER** commit this key to Git or package it in the Android application.

### Step 3.2: Deploy Firestore Security Rules & Indexes
Run the Firebase CLI in the root directory:

```bash
# 1. Login to Firebase CLI
firebase login

# 2. Select project
firebase use audit-system-e7707

# 3. Deploy security rules and indexes
firebase deploy --only firestore:rules,firestore:indexes
```

### Step 3.3: One-Time Provisioning of SaaS Platform Owner
Set the environment variable pointing to your service account key and execute:

```bash
cd server_provisioning
npm install

export GOOGLE_APPLICATION_CREDENTIALS="/path/to/serviceAccountKey.json"
node provision_saas_owner.js --email Zawaruldo69@gmail.com --password "<YourSecurePassword>"
```

This assigns `{ platform_admin: true, role: 'SAAS_OWNER' }` to your account in Firebase Authentication and initializes the `/platform_analytics/global_summary` document.

### Step 3.4: Provision a Tenant Supermarket & Store Owner
To onboard a new supermarket:

```bash
node provision_tenant_store.js \
  --storeId "STR-NAB-001" \
  --storeName "Al-Makhzen Supermarket" \
  --region "Nabatieh Area, South Lebanon" \
  --ownerEmail "owner@almakhzen.com" \
  --ownerPassword "StoreOwnerPass2026!"
```

### Step 3.5: Add or Manage Store Employees
```bash
# Add Cashier
node manage_store_employee.js create \
  --storeId "STR-NAB-001" \
  --email "cashier1@almakhzen.com" \
  --password "CashierPass2026!" \
  --role "CASHIER" \
  --displayName "Hassan Cashier"

# Add Accountant
node manage_store_employee.js create \
  --storeId "STR-NAB-001" \
  --email "accountant@almakhzen.com" \
  --password "AccountantPass2026!" \
  --role "ACCOUNTANT" \
  --displayName "Fatima Accountant"

# Disable Employee
node manage_store_employee.js disable \
  --storeId "STR-NAB-001" \
  --email "cashier1@almakhzen.com"
```

---

## 4. Testing & Verification

Run the rule verification script:
```bash
node server_provisioning/test_security_rules.js
```
