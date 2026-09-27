/**
 * Server-Side Provisioning: SaaS Platform Owner Account
 * 
 * Sets verified custom claims { platform_admin: true, role: 'SAAS_OWNER' }
 * using the Firebase Admin SDK.
 * 
 * Usage:
 *   export GOOGLE_APPLICATION_CREDENTIALS="/path/to/serviceAccountKey.json"
 *   node provision_saas_owner.js --email Zawaruldo69@gmail.com [--uid <uid>] [--password <initialPassword>]
 * 
 * Invariants:
 *   - Never executed inside the Android client app.
 *   - Service account keys remain securely on the server/CLI environment.
 *   - Client code cannot grant itself platform_admin or SAAS_OWNER.
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

const targetEmail = params.email || 'Zawaruldo69@gmail.com';
const customUid = params.uid;
const initialPassword = params.password || 'AdminSuperPass2026!';

// Initialize Firebase Admin SDK
if (!admin.apps.length) {
  const projectId = process.env.FIREBASE_PROJECT_ID || 'audit-system-e7707';
  admin.initializeApp({
    projectId: projectId
  });
}

const auth = admin.auth();
const db = admin.firestore();

async function provisionSaaSOwner() {
  console.log(`====================================================`);
  console.log(`Provisioning SaaS Platform Owner: ${targetEmail}`);
  console.log(`Target Firebase Project: ${admin.app().options.projectId}`);
  console.log(`====================================================\n`);

  let userRecord;
  try {
    userRecord = await auth.getUserByEmail(targetEmail);
    console.log(`[1/3] Found existing Firebase Auth account for ${targetEmail} (UID: ${userRecord.uid})`);
  } catch (err) {
    if (err.code === 'auth/user-not-found') {
      console.log(`[1/3] User not found. Creating new Firebase Auth account for ${targetEmail}...`);
      userRecord = await auth.createUser({
        uid: customUid,
        email: targetEmail,
        password: initialPassword,
        emailVerified: true,
        displayName: 'SaaS Platform Owner'
      });
      console.log(`[1/3] Created user UID: ${userRecord.uid}`);
    } else {
      console.error(`Error looking up user: ${err.message}`);
      process.exit(1);
    }
  }

  // 2. Set Trusted Custom Claims on Firebase Authentication
  console.log(`[2/3] Setting authoritative custom claims: { platform_admin: true, role: 'SAAS_OWNER' }`);
  await auth.setCustomUserClaims(userRecord.uid, {
    platform_admin: true,
    role: 'SAAS_OWNER'
  });

  // Verify claims
  const updatedUser = await auth.getUser(userRecord.uid);
  console.log(`[2/3] Confirmed updated claims:`, updatedUser.customClaims);

  // 3. Initialize or update global summary in Firestore (without fake stores)
  console.log(`[3/3] Initializing /platform_analytics/global_summary metadata...`);
  const analyticsRef = db.collection('platform_analytics').document('global_summary');
  const existingDoc = await analyticsRef.get();

  if (!existingDoc.exists) {
    await analyticsRef.set({
      total_stores: 0,
      total_users: 1,
      total_sales_volume_usd: 0.0,
      total_transactions_count: 0,
      total_inventory_skus: 0,
      total_low_stock_alerts: 0,
      total_audit_events: 0,
      total_sessions_count: 1,
      feature_usage: {},
      suspicious_events_count: 0,
      last_updated: admin.firestore.FieldValue.serverTimestamp(),
      initialized_by: userRecord.uid,
      owner_email: targetEmail
    });
    console.log(`[3/3] Created initial /platform_analytics/global_summary`);
  } else {
    console.log(`[3/3] /platform_analytics/global_summary already initialized.`);
  }

  console.log(`\n====================================================`);
  console.log(`PROVISIONING COMPLETED SUCCESSFULLY!`);
  console.log(`UID: ${userRecord.uid}`);
  console.log(`Email: ${targetEmail}`);
  console.log(`Claims: { platform_admin: true, role: 'SAAS_OWNER' }`);
  console.log(`\nNext Steps:`);
  console.log(`1. In the Android Supermarket POS app, sign in with ${targetEmail}`);
  console.log(`2. The app will detect platform_admin == true and enable the SaaS Owner Command Center.`);
  console.log(`====================================================\n`);
}

provisionSaaSOwner().catch((err) => {
  console.error(`Provisioning Failed:`, err);
  process.exit(1);
});
