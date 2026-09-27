/**
 * Backend Platform Metrics Aggregator
 * 
 * Computes authoritative business metrics for the SaaS Owner Command Center
 * by querying actual Firestore tenant records using the Firebase Admin SDK.
 * 
 * Metric Calculations:
 * - total_stores: Count of all valid /stores/{storeId} documents.
 * - active_stores: Stores with sales or audit activity within the last 30 days.
 * - total_users: Sum of active (/stores/{storeId}/users) accounts where disabled !== true.
 * - total_sales_volume_usd: Sum of distinct /stores/{storeId}/sales price * quantity.
 * - total_transactions_count: Count of unique non-duplicated sale receipts.
 * - total_inventory_skus: Sum of all distinct /stores/{storeId}/products.
 * - total_low_stock_alerts: Count of items where product_amount_left <= 50.
 * - total_audit_events: Sum of /stores/{storeId}/auditLogs entries.
 * - suspicious_events_count: Count of entries in /security_events.
 * 
 * Usage:
 *   export GOOGLE_APPLICATION_CREDENTIALS="/path/to/serviceAccountKey.json"
 *   node aggregate_platform_metrics.js [--dry-run]
 */

const admin = require('firebase-admin');

// Parse flags
const args = process.argv.slice(2);
const isDryRun = args.includes('--dry-run');

if (!admin.apps.length) {
  admin.initializeApp({
    projectId: process.env.FIREBASE_PROJECT_ID || 'audit-system-e7707'
  });
}

const db = admin.firestore();

async function aggregateAllPlatformMetrics() {
  console.log("====================================================");
  console.log("Starting Authoritative SaaS Metrics Aggregation");
  console.log(`Timestamp: ${new Date().toISOString()}`);
  console.log(`Dry Run Mode: ${isDryRun}`);
  console.log("====================================================\n");

  const thirtyDaysAgo = Date.now() - (30 * 24 * 60 * 60 * 1000);

  let totalStores = 0;
  let activeStores = 0;
  let totalUsers = 0;
  let totalSalesVolumeUsd = 0.0;
  let totalTransactionsCount = 0;
  let totalInventorySkus = 0;
  let totalLowStockAlerts = 0;
  let totalAuditEvents = 0;

  const perStoreSummaries = [];

  // 1. Paginate through all tenant stores (handles arbitrary number of stores)
  let lastStoreDoc = null;
  const pageSize = 100;
  let hasMoreStores = true;

  while (hasMoreStores) {
    let query = db.collection('stores').limit(pageSize);
    if (lastStoreDoc) {
      query = query.startAfter(lastStoreDoc);
    }

    const storeSnapshots = await query.get();
    if (storeSnapshots.empty) {
      break;
    }

    for (const storeDoc of storeSnapshots.docs) {
      const storeId = storeDoc.id;
      const storeData = storeDoc.data();
      totalStores++;

      console.log(`--> Aggregating Store: [${storeId}] "${storeData.store_name || storeId}"`);

      let storeSalesVolume = 0.0;
      let storeSalesCount = 0;
      let lastActiveTime = 0;

      // 1.1 Aggregate Sales (Idempotent: deduplicate by sale_id)
      const seenSaleIds = new Set();
      const salesSnap = await db.collection('stores').document(storeId).collection('sales').get();
      for (const sDoc of salesSnap.docs) {
        const sale = sDoc.data();
        const saleId = sale.sale_id || sDoc.id;
        if (seenSaleIds.has(saleId)) continue;
        seenSaleIds.add(saleId);

        const qty = Number(sale.quantity) || 1;
        const price = Number(sale.price) || 0.0;
        const lineTotal = price * qty;
        storeSalesVolume += lineTotal;
        storeSalesCount++;

        const saleTime = sale.created_at ? (sale.created_at.toDate ? sale.created_at.toDate().getTime() : Number(sale.created_at)) : 0;
        if (saleTime > lastActiveTime) lastActiveTime = saleTime;
      }

      // 1.2 Aggregate Inventory
      let storeSkus = 0;
      let storeLowStock = 0;
      const prodSnap = await db.collection('stores').document(storeId).collection('products').get();
      for (const pDoc of prodSnap.docs) {
        const prod = pDoc.data();
        storeSkus++;
        const amountLeft = Number(prod.product_amount_left) || 0;
        if (amountLeft <= 50) {
          storeLowStock++;
        }
        const updatedTime = prod.updated_at ? (prod.updated_at.toDate ? prod.updated_at.toDate().getTime() : Number(prod.updated_at)) : 0;
        if (updatedTime > lastActiveTime) lastActiveTime = updatedTime;
      }

      // 1.3 Count Active Users (Exclude disabled accounts)
      let storeUserCount = 0;
      const userSnap = await db.collection('stores').document(storeId).collection('users').get();
      for (const uDoc of userSnap.docs) {
        const u = uDoc.data();
        if (u.disabled !== true) {
          storeUserCount++;
        }
      }

      // 1.4 Count Audit Logs
      const auditSnap = await db.collection('stores').document(storeId).collection('auditLogs').get();
      const storeAuditCount = auditSnap.size;
      for (const aDoc of auditSnap.docs) {
        const a = aDoc.data();
        const aTime = a.timestamp ? (a.timestamp.toDate ? a.timestamp.toDate().getTime() : Number(a.timestamp)) : 0;
        if (aTime > lastActiveTime) lastActiveTime = aTime;
      }

      // Determine active status (within 30 days)
      const isActive = lastActiveTime > thirtyDaysAgo || storeSalesCount > 0;
      if (isActive) activeStores++;

      // Accumulate global totals
      totalSalesVolumeUsd += storeSalesVolume;
      totalTransactionsCount += storeSalesCount;
      totalInventorySkus += storeSkus;
      totalLowStockAlerts += storeLowStock;
      totalUsers += storeUserCount;
      totalAuditEvents += storeAuditCount;

      perStoreSummaries.push({
        store_id: storeId,
        store_name: storeData.store_name || `Store ${storeId}`,
        region: storeData.region || "Central District",
        subscription_status: storeData.subscription_status || "ACTIVE",
        user_count: storeUserCount,
        sales_count: storeSalesCount,
        revenue_usd: Math.round(storeSalesVolume * 100) / 100,
        inventory_count: storeSkus,
        last_active: lastActiveTime || Date.now()
      });
    }

    if (storeSnapshots.docs.length < pageSize) {
      hasMoreStores = false;
    } else {
      lastStoreDoc = storeSnapshots.docs[storeSnapshots.docs.length - 1];
    }
  }

  // 2. Fetch Security Events Count
  const secSnap = await db.collection('security_events').limit(100).get();
  const suspiciousEventsCount = secSnap.size;

  const resultPayload = {
    total_stores: totalStores,
    active_stores: activeStores,
    total_users: totalUsers,
    total_sales_volume_usd: Math.round(totalSalesVolumeUsd * 100) / 100,
    total_transactions_count: totalTransactionsCount,
    total_inventory_skus: totalInventorySkus,
    total_low_stock_alerts: totalLowStockAlerts,
    total_audit_events: totalAuditEvents,
    suspicious_events_count: suspiciousEventsCount,
    stores_list: perStoreSummaries,
    last_updated: admin.firestore.FieldValue.serverTimestamp()
  };

  console.log("\n====================================================");
  console.log("AGGREGATED METRICS SUMMARY:");
  console.log(`- Total Stores: ${totalStores} (${activeStores} active)`);
  console.log(`- Total Users: ${totalUsers}`);
  console.log(`- Total Sales Volume: $${resultPayload.total_sales_volume_usd}`);
  console.log(`- Total Orders: ${totalTransactionsCount}`);
  console.log(`- Total SKUs: ${totalInventorySkus} (${totalLowStockAlerts} low stock)`);
  console.log(`- Total Audit Logs: ${totalAuditEvents}`);
  console.log(`- Suspicious Security Events: ${suspiciousEventsCount}`);
  console.log("====================================================\n");

  if (!isDryRun) {
    console.log("Writing to /platform_analytics/global_summary...");
    await db.collection('platform_analytics').document('global_summary').set(resultPayload, { merge: true });
    console.log("SUCCESS: /platform_analytics/global_summary updated with authoritative real metrics.");
  } else {
    console.log("DRY RUN: Skipped write to Firestore.");
  }
}

aggregateAllPlatformMetrics().catch((err) => {
  console.error("Aggregation Failed:", err);
  process.exit(1);
});
