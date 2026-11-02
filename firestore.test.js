// Run with the emulator:  npm i -D @firebase/rules-unit-testing jest && firebase emulators:exec --only firestore "npx jest firestore.test.js"
// Covers the anti-cheat guarantees of firestore.rules. (Not executed in the review environment: no network/emulator.)
const { initializeTestEnvironment, assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');
const { serverTimestamp, doc, setDoc, updateDoc, getDoc, writeBatch, deleteDoc } = require('firebase/firestore');
const fs = require('fs');

let env;
beforeAll(async () => {
  env = await initializeTestEnvironment({ projectId: 'asphalt-test', firestore: { rules: fs.readFileSync('firestore.rules', 'utf8') } });
});
afterAll(() => env.cleanup());
beforeEach(() => env.clearFirestore());

const ctx = (uid, phone, email) => env.authenticatedContext(uid, { phone_number: phone, email, email_verified: true }).firestore();
const driver = (uid, phone, email, days, plan, dev) => ({
  userId: uid, driverName: 'T', phone, email, deviceId: dev, trialClaimed: true, governorate: 'القاهرة', engineCc: 150,
  vehicleType: 'MOTORCYCLE', remainingDays: days, isSessionActive: false, isReaderActive: false, currentPlan: plan,
  totalSessionsStarted: 0, totalRidesScanned: 0, totalNetProfit: 0, role: 'driver',
  createdAt: serverTimestamp(), updatedAt: serverTimestamp(), lastActiveTimestamp: serverTimestamp(),
});
const claim = (uid, key, dev) => ({ phone: key, userId: uid, deviceId: dev, claimedAt: serverTimestamp() });

async function register(db, uid, phone, email, dev) {
  const key = phone.replace('+', '');
  const b = writeBatch(db);
  b.set(doc(db, 'claimed_trials', key), claim(uid, key, dev));
  b.set(doc(db, 'claimed_trials', 'dev_' + dev), claim(uid, key, dev));
  b.set(doc(db, 'drivers', uid), driver(uid, phone, email, 3, 'FREE_TRIAL_3_DAYS', dev));
  return b.commit();
}

test('first registration gets 3 days atomically', async () => {
  await assertSucceeds(register(ctx('u1', '+201000000001', 'a@x.com'), 'u1', '+201000000001', 'a@x.com', 'D1'));
});

test('second Google account on same device cannot get a trial', async () => {
  await register(ctx('u1', '+201000000001', 'a@x.com'), 'u1', '+201000000001', 'a@x.com', 'D1');
  await assertFails(register(ctx('u2', '+201000000002', 'b@x.com'), 'u2', '+201000000002', 'b@x.com', 'D1'));
  await assertSucceeds(setDoc(doc(ctx('u2', '+201000000002', 'b@x.com'), 'drivers', 'u2'),
    driver('u2', '+201000000002', 'b@x.com', 0, 'EXPIRED_TRIAL', 'D1')));
});

test('client cannot self-grant days or pick 3 days without claims', async () => {
  const db = ctx('u3', '+201000000003', 'c@x.com');
  await assertFails(setDoc(doc(db, 'drivers', 'u3'), driver('u3', '+201000000003', 'c@x.com', 3, 'FREE_TRIAL_3_DAYS', 'D3')));
  await assertFails(setDoc(doc(db, 'drivers', 'u3'), driver('u3', '+201000000003', 'c@x.com', 999, 'PAID', 'D3')));
});

test('balance cannot be edited, only decremented by exactly 1 via a server-timestamped session start', async () => {
  const db = ctx('u1', '+201000000001', 'a@x.com');
  await register(db, 'u1', '+201000000001', 'a@x.com', 'D1');
  const ref = doc(db, 'drivers', 'u1');
  await assertFails(updateDoc(ref, { remainingDays: 100 }));
  await assertFails(updateDoc(ref, { remainingDays: 2 })); // missing start/session fields
  await assertSucceeds(updateDoc(ref, { remainingDays: 2, activeSessionStartTime: serverTimestamp(), isSessionActive: true,
    isReaderActive: true, totalSessionsStarted: 1, lastActiveTimestamp: serverTimestamp(), updatedAt: serverTimestamp() }));
  // second start inside the same 24h is refused
  await assertFails(updateDoc(ref, { remainingDays: 1, activeSessionStartTime: serverTimestamp(), isSessionActive: true,
    isReaderActive: true, totalSessionsStarted: 2, lastActiveTimestamp: serverTimestamp(), updatedAt: serverTimestamp() }));
});

test('cannot delete profile or claims, cannot read other drivers, cannot list', async () => {
  const db = ctx('u1', '+201000000001', 'a@x.com');
  await register(db, 'u1', '+201000000001', 'a@x.com', 'D1');
  await assertFails(deleteDoc(doc(db, 'drivers', 'u1')));
  await assertFails(deleteDoc(doc(db, 'claimed_trials', '201000000001')));
  await assertFails(getDoc(doc(ctx('u9', '+201000000009', 'z@x.com'), 'drivers', 'u1')));
});

test('only admins can write subscriptions', async () => {
  await env.withSecurityRulesDisabled(async (c) => setDoc(doc(c.firestore(), 'admins', 'admin1'), { ok: true }));
  const sub = { driverId: 'u1', planType: 'ADMIN_ACTIVATION', daysAdded: 30, amountPaid: 150, createdAt: serverTimestamp() };
  await assertFails(setDoc(doc(ctx('u1', '+201000000001', 'a@x.com'), 'subscriptions', 's1'), sub));
  await assertSucceeds(setDoc(doc(ctx('admin1', '+201000000100', 'adm@x.com'), 'subscriptions', 's1'), sub));
});
