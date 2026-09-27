/**
 * Server-Side Provisioning: Tenant Store & Store Owner
 * 
 * Provisions:
 * 1. Store metadata document under /stores/{storeId}
 * 2. Store Owner account in Firebase Auth with custom claims:
 *    { store_id: storeId, role: 'OWNER', platform_admin: false }
 * 3. Store User profile document under /stores/{storeId}/users/{uid}
 * 
 * Usage:
 *   node provision_tenant_store.js \
 *     --storeId "STR-NAB-001" \
 *     --storeName "Al-Makhzen Supermarket" \
 *     --region "Nabatieh, Lebanon" \
 *     --ownerEmail "owner@almakhzen.com" \
 *     --ownerPassword "StoreOwnerPass2026!"
 */

const admin = require('firebase-admin');

// Parse CLI arguments
const args = process.argv.slice(2);
const params = {};
for (let i = 0; i < args.length; i += 2) {
  const key = args[i].replace(/^--/, '');
  const val = args[i + 1];
  params[key] = val;
}

const storeId = params.storeId;
const storeName = params.storeName || `Store ${storeId}`;
const region = params.region || 'Central District';
const ownerEmail = params.ownerEmail;
const ownerPassword = params.ownerPassword || 'StoreOwnerPass2026!';

if (!storeId || !ownerEmail) {
  console.error("Usage: node provision_tenant_store.js --storeId <id> --ownerEmail <email> [--storeName <name>] [--region <region>] [--ownerPassword <pass>]");
  process.exit(1);
}

if (!admin.apps.length) {
  admin.initializeApp({
    projectId: process.env.FIREBASE_PROJECT_ID || 'audit-system-e7707'
  });
}

const auth = admin.auth();
const db = admin.firestore();

async function provisionTenantStore() {
  console.log(`====================================================`);
  console.log(`Provisioning Tenant Store: [${storeId}] "${storeName}"`);
  console.log(`Store Owner: ${ownerEmail}`);
  console.log(`====================================================\n`);

  // 1. Create or fetch store document in Firestore
  const storeRef = db.collection('stores').document(storeId);
  await storeRef.set({
    store_id: storeId,
    store_name: storeName,
    region: region,
    subscription_status: 'ACTIVE',
    currency: 'USD / LBP',
    active_terminal_id: 'TERM-01',
    created_at: admin.firestore.FieldValue.serverTimestamp(),
    updated_at: admin.firestore.FieldValue.serverTimestamp()
  }, { merge: true });
  console.log(`[1/3] Store document created/updated at /stores/${storeId}`);

  // 2. Create or fetch Owner account in Firebase Auth
  let userRecord;
  try {
    userRecord = await auth.getUserByEmail(ownerEmail);
    console.log(`[2/3] Found existing user account (UID: ${userRecord.uid})`);
  } catch (err) {
    if (err.code === 'auth/user-not-found') {
      userRecord = await auth.createUser({
        email: ownerEmail,
        password: ownerPassword,
        displayName: `${storeName} Owner`,
        emailVerified: true
      });
      console.log(`[2/3] Created new Firebase Auth account (UID: ${userRecord.uid})`);
    } else {
      throw err;
    }
  }

  // Authoritative custom claims for tenant owner
  await auth.setCustomUserClaims(userRecord.uid, {
    store_id: storeId,
    role: 'OWNER',
    platform_admin: false
  });
  console.log(`[2/3] Assigned custom claims: { store_id: '${storeId}', role: 'OWNER', platform_admin: false }`);

  // 3. Create user profile in /stores/{storeId}/users/{uid}
  const userProfileRef = storeRef.collection('users').document(userRecord.uid);
  await userProfileRef.set({
    uid: userRecord.uid,
    email: ownerEmail,
    displayName: `${storeName} Owner`,
    role: 'OWNER',
    store_id: storeId,
    disabled: false,
    created_at: admin.firestore.FieldValue.serverTimestamp(),
    updated_at: admin.firestore.FieldValue.serverTimestamp()
  }, { merge: true });
  console.log(`[3/3] User profile stored at /stores/${storeId}/users/${userRecord.uid}`);

  console.log(`\n====================================================`);
  console.log(`TENANT STORE PROVISIONED SUCCESSFULLY!`);
  console.log(`Store ID: ${storeId}`);
  console.log(`Owner UID: ${userRecord.uid}`);
  console.log(`Owner Email: ${ownerEmail}`);
  console.log(`====================================================\n`);
}

provisionTenantStore().catch((err) => {
  console.error(`Store Provisioning Failed:`, err);
  process.exit(1);
});
