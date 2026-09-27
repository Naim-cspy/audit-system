/**
 * Server-Side Google Analytics 4 (GA4) Reporting Integration
 * 
 * Fetches actual usage telemetry from the Google Analytics Data API (runReport):
 * - Total sessions
 * - Total logins
 * - Feature usage event distribution
 * 
 * Secure SaaS Invariants:
 * - Credentials remain strictly on the backend/server; never packaged into Android APK.
 * - Platform metrics are written to /platform_analytics/global_summary for SaaS Owner access.
 * - If GA4_PROPERTY_ID is not configured, marks ga_reporting_configured = false so the
 *   Android dashboard displays "Not configured" rather than fabricated metrics.
 * 
 * Usage:
 *   export GOOGLE_APPLICATION_CREDENTIALS="/path/to/serviceAccountKey.json"
 *   export GA4_PROPERTY_ID="123456789"
 *   node fetch_ga4_reporting.js
 */

const admin = require('firebase-admin');
const https = require('https');

if (!admin.apps.length) {
  admin.initializeApp({
    projectId: process.env.FIREBASE_PROJECT_ID || 'audit-system-e7707'
  });
}

const db = admin.firestore();
const propertyId = process.env.GA4_PROPERTY_ID;

async function runGA4Report(accessToken, propertyId, requestBody) {
  return new Promise((resolve, reject) => {
    const postData = JSON.stringify(requestBody);
    const options = {
      hostname: 'analyticsdata.googleapis.com',
      port: 443,
      path: `/v1beta/properties/${propertyId}:runReport`,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(postData),
        'Authorization': `Bearer ${accessToken}`
      }
    };

    const req = https.request(options, (res) => {
      let data = '';
      res.on('data', (chunk) => { data += chunk; });
      res.on('end', () => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          try {
            resolve(JSON.parse(data));
          } catch (e) {
            reject(new Error(`Failed to parse GA4 response: ${e.message}`));
          }
        } else {
          reject(new Error(`GA4 API error HTTP ${res.statusCode}: ${data}`));
        }
      });
    });

    req.on('error', (err) => reject(err));
    req.write(postData);
    req.end();
  });
}

async function fetchAndSyncGA4Metrics() {
  console.log("====================================================");
  console.log("Google Analytics 4 Server-Side Reporting Pipeline");
  console.log(`Execution Time: ${new Date().toISOString()}`);
  console.log("====================================================\n");

  if (!propertyId || propertyId.trim() === '') {
    console.log("[STATUS] GA4_PROPERTY_ID is not configured in environment.");
    console.log("[INFO]   Android clients are actively streaming telemetry via Firebase Analytics SDK (verifiable in DebugView).");
    console.log("[INFO]   Setting ga_reporting_configured = false in /platform_analytics/global_summary.");
    console.log("[INFO]   Owner dashboard will display 'Not configured' in accordance with truthfulness requirements.\n");

    await db.collection('platform_analytics').document('global_summary').set({
      ga_reporting_configured: false,
      total_sessions_count: null,
      total_logins_count: null,
      ga_last_checked: admin.firestore.FieldValue.serverTimestamp()
    }, { merge: true });

    console.log("[SUCCESS] Updated /platform_analytics/global_summary with unconfigured status.");
    return;
  }

  console.log(`[INFO] Querying GA4 Property ID: ${propertyId}...`);

  // Obtain OAuth2 access token using Google Application Default Credentials
  const { GoogleAuth } = require('google-auth-library');
  const auth = new GoogleAuth({
    scopes: ['https://www.googleapis.com/auth/analytics.readonly']
  });
  const client = await auth.getClient();
  const tokenResponse = await client.getAccessToken();
  const accessToken = tokenResponse.token;

  if (!accessToken) {
    throw new Error("Failed to obtain OAuth2 access token for Google Analytics API");
  }

  // 1. Query Overall Sessions & Active Users (Last 30 Days)
  const sessionsReport = await runGA4Report(accessToken, propertyId, {
    dateRanges: [{ startDate: '30daysAgo', endDate: 'today' }],
    metrics: [
      { name: 'sessions' },
      { name: 'activeUsers' },
      { name: 'eventCount' }
    ]
  });

  const totalSessions = parseInt(sessionsReport.rows?.[0]?.metricValues?.[0]?.value || '0', 10);
  const activeUsers = parseInt(sessionsReport.rows?.[0]?.metricValues?.[1]?.value || '0', 10);

  // 2. Query Event Breakdown for Feature Usage
  const eventsReport = await runGA4Report(accessToken, propertyId, {
    dateRanges: [{ startDate: '30daysAgo', endDate: 'today' }],
    dimensions: [{ name: 'eventName' }],
    metrics: [{ name: 'eventCount' }]
  });

  let totalLogins = 0;
  const featureUsage = {};

  if (eventsReport.rows) {
    for (const row of eventsReport.rows) {
      const eventName = row.dimensionValues[0]?.value;
      const count = parseInt(row.metricValues[0]?.value || '0', 10);

      if (eventName === 'login') {
        totalLogins += count;
      }

      // Map documented events to human-readable feature names
      const featureLabel = mapEventToFeatureLabel(eventName);
      if (featureLabel) {
        featureUsage[featureLabel] = (featureUsage[featureLabel] || 0) + count;
      }
    }
  }

  console.log("\n[GA4 METRICS RECEIVED]:");
  console.log(`- 30-Day Sessions: ${totalSessions}`);
  console.log(`- Active Users: ${activeUsers}`);
  console.log(`- Logins Tracked: ${totalLogins}`);
  console.log("- Feature Usage Breakdown:", featureUsage);

  // 3. Write Authoritative GA4 Metrics to Firestore
  await db.collection('platform_analytics').document('global_summary').set({
    ga_reporting_configured: true,
    total_sessions_count: totalSessions,
    total_logins_count: totalLogins,
    feature_usage: featureUsage,
    ga_last_synced: admin.firestore.FieldValue.serverTimestamp()
  }, { merge: true });

  console.log("\n[SUCCESS] Authoritative GA4 usage telemetry synced to /platform_analytics/global_summary.");
}

function mapEventToFeatureLabel(eventName) {
  switch (eventName) {
    case 'purchase':
      return 'POS Checkout';
    case 'inventory_update':
      return 'Inventory Management';
    case 'screen_view':
      return 'Screen Navigation';
    case 'select_content':
      return 'Action Selection';
    case 'security_alert':
      return 'Security Alert Monitoring';
    case 'login':
      return 'User Authentication';
    default:
      return null;
  }
}

fetchAndSyncGA4Metrics().catch((err) => {
  console.error("[ERROR] GA4 Reporting Pipeline Failed:", err.message);
  process.exit(1);
});
