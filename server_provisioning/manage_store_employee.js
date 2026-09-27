/**
 * Server-Side Employee Account Management
 * 
 * Manages employee accounts directly across Firebase Authentication and Firestore.
 * 
 * Commands:
 *   node manage_store_employee.js create --storeId <id> --email <email> --password <pass> --role <OWNER|ADMIN|MANAGER|CASHIER|ACCOUNTANT> [--displayName <name>]
 *   node manage_store_employee.js disable --storeId <id> --email <email>
 *   node manage_store_employee.js enable --storeId <id> --email <email>
 *   node manage_store_employee.js delete --storeId <id> --email <email>
 *   node manage_store_employee.js set-password --email <email> --password <newPass>
 */

const admin = require('firebase-admin');

const action = process.argv[2];
const args = process.argv.slice(3);
const params = {};
for (let i = 0; i < args.length; i += 2) {
  const key = args[i].replace(/^--/, '');
  const val = args[i + 1];
  params[key] = val;
}

if (!admin.apps.length) {
  admin.initializeApp({
    projectId: process.env.FIREBASE_PROJECT_ID || 'audit-system-e7707'
  });
}

const auth = admin.auth();
const db = admin.firestore();

const SUPPORTED_ROLES = ['OWNER', 'ADMIN', 'MANAGER', 'CASHIER', 'ACCOUNTANT'];

async function run() {
  if (!action) {
    console.error("Usage: node manage_store_employee.js <create|disable|enable|delete|set-password> [options]");
    process.exit(1);
  }

  const { storeId, email, password, role, displayName } = params;

  switch (action) {
    case 'create': {
      if (!storeId || !email || !password || !role) {
        console.error("Error: --storeId, --email, --password, and --role are required to create an employee.");
        process.exit(1);
      }
      const upperRole = role.toUpperCase();
      if (!SUPPORTED_ROLES.includes(upperRole)) {
        console.error(`Error: Unsupported role '${role}'. Supported: ${SUPPORTED_ROLES.join(', ')}`);
        process.exit(1);
      }

      console.log(`Creating employee '${email}' with role '${upperRole}' for store '${storeId}'...`);
      const user = await auth.createUser({
        email: email,
        password: password,
        displayName: displayName || email.split('@')[0],
        emailVerified: true
      });

      await auth.setCustomUserClaims(user.uid, {
        store_id: storeId,
        role: upperRole,
        platform_admin: false
      });

      await db.collection('stores').document(storeId)
        .collection('users').document(user.uid)
        .set({
          uid: user.uid,
          email: email,
          displayName: displayName || email.split('@')[0],
          role: upperRole,
          store_id: storeId,
          disabled: false,
          created_at: admin.firestore.FieldValue.serverTimestamp(),
          updated_at: admin.firestore.FieldValue.serverTimestamp()
        });

      console.log(`SUCCESS: Employee created UID: ${user.uid} with claims { store_id: '${storeId}', role: '${upperRole}' }`);
      break;
    }

    case 'disable': {
      if (!storeId || !email) {
        console.error("Error: --storeId and --email required to disable user.");
        process.exit(1);
      }
      const user = await auth.getUserByEmail(email);
      await auth.updateUser(user.uid, { disabled: true });
      await db.collection('stores').document(storeId)
        .collection('users').document(user.uid)
        .update({ disabled: true, updated_at: admin.firestore.FieldValue.serverTimestamp() });
      console.log(`SUCCESS: Employee '${email}' disabled in Firebase Auth and Firestore.`);
      break;
    }

    case 'enable': {
      if (!storeId || !email) {
        console.error("Error: --storeId and --email required to enable user.");
        process.exit(1);
      }
      const user = await auth.getUserByEmail(email);
      await auth.updateUser(user.uid, { disabled: false });
      await db.collection('stores').document(storeId)
        .collection('users').document(user.uid)
        .update({ disabled: false, updated_at: admin.firestore.FieldValue.serverTimestamp() });
      console.log(`SUCCESS: Employee '${email}' re-enabled.`);
      break;
    }

    case 'delete': {
      if (!storeId || !email) {
        console.error("Error: --storeId and --email required to delete user.");
        process.exit(1);
      }
      const user = await auth.getUserByEmail(email);
      await auth.deleteUser(user.uid);
      await db.collection('stores').document(storeId)
        .collection('users').document(user.uid)
        .delete();
      console.log(`SUCCESS: Employee '${email}' deleted from Firebase Auth and Firestore.`);
      break;
    }

    case 'set-password': {
      if (!email || !password) {
        console.error("Error: --email and --password required.");
        process.exit(1);
      }
      const user = await auth.getUserByEmail(email);
      await auth.updateUser(user.uid, { password: password });
      console.log(`SUCCESS: Password updated for user '${email}'.`);
      break;
    }

    default:
      console.error(`Unknown action: ${action}`);
      process.exit(1);
  }
}

run().catch((err) => {
  console.error("Operation failed:", err.message);
  process.exit(1);
});
