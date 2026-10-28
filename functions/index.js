const { onDocumentCreated, onDocumentUpdated } = require('firebase-functions/v2/firestore');
const admin = require('firebase-admin');
const words = require('./words');

admin.initializeApp();
const db = admin.firestore();

// ---------------------------------------------------------------------------
// 1) إشعارات الرسائل
// ---------------------------------------------------------------------------
exports.onMessageCreated = onDocumentCreated('chats/{chatId}/messages/{msgId}', async (event) => {
  const m = event.data && event.data.data();
  if (!m || !m.from || typeof m.text !== 'string') return;

  const parts = event.params.chatId.split('_');
  const to = parts.find((p) => p !== m.from);
  if (!to || !parts.includes(m.from)) return;

  // إذا المستلم حاظر المرسل ما نرسل إشعار
  const blocked = await db.doc(`blocks/${to}/users/${m.from}`).get();
  if (blocked.exists) return;

  // توكنات أجهزة المستلم (إذا طلع من حسابه ينحذف توكنه، فما يوصله شي)
  const tokensSnap = await db.collection(`tokens/${to}/devices`).get();
  const tokens = tokensSnap.docs.map((d) => d.id);
  if (tokens.length === 0) return;

  let name = '';
  const prof = await db.doc(`profiles/${m.from}`).get();
  if (prof.exists) name = prof.get('name') || '';
  if (!name) {
    const u = await db.doc(`users/${m.from}`).get();
    if (u.exists) name = u.get('name') || '';
  }

  // data فقط (بدون notification) حتى التطبيق هو الي يبني الإشعار بصورة المرسل
  const res = await admin.messaging().sendEachForMulticast({
    tokens,
    data: {
      type: 'msg',
      from: m.from,
      to,
      fromName: name,
      text: m.text.slice(0, 200),
      chatId: event.params.chatId
    },
    // ttl قصير: إذا الجهاز بدون إنترنت ما يوصله الإشعار لاحقاً
    android: { priority: 'high', ttl: 120 * 1000 }
  });

  // تنظيف التوكنات الميتة
  const dead = [];
  res.responses.forEach((r, i) => {
    const code = r.error && r.error.code;
    if (code === 'messaging/registration-token-not-registered' || code === 'messaging/invalid-registration-token') {
      dead.push(tokens[i]);
    }
  });
  await Promise.all(dead.map((t) => db.doc(`tokens/${to}/devices/${t}`).delete()));
});

// ---------------------------------------------------------------------------
// 2) البلاغات
// ---------------------------------------------------------------------------

// توحيد النص العربي قبل الفحص
function normalize(s) {
  return String(s)
    .replace(/[\u064B-\u065F\u0670\u0640]/g, '')
    .replace(/[أإآ]/g, 'ا')
    .replace(/ى/g, 'ي')
    .replace(/ة/g, 'ه')
    .replace(/[^\u0600-\u06FFa-zA-Z0-9\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase();
}

function matchList(text, list) {
  const t = ' ' + normalize(text) + ' ';
  return list.some((w) => t.includes(' ' + normalize(w) + ' ') || (w.includes(' ') && t.includes(normalize(w))));
}

function detect(text) {
  if (matchList(text, words.abuse)) return 'abuse';
  if (matchList(text, words.violence)) return 'violence';
  if (matchList(text, words.suicide_kill)) return 'suicide_kill';
  if (matchList(text, words.selfHarm)) return 'selfHarm';
  return null;
}

// مدة الحظر بالأيام: 1، 3، 7، ثم تتضاعف (14، 28، 56، ...) بحد أقصى سنة
function banDays(strikes) {
  if (strikes === 1) return 1;
  if (strikes === 2) return 3;
  if (strikes === 3) return 7;
  return Math.min(365, 7 * Math.pow(2, strikes - 3));
}

async function applyViolation(reportedUid, msgRefs, reason) {
  return db.runTransaction(async (tx) => {
    const modRef = db.doc(`moderation/${reportedUid}`);
    const mod = await tx.get(modRef);
    const strikes = (mod.exists ? mod.get('strikes') || 0 : 0) + 1;
    const days = banDays(strikes);
    const until = Date.now() + days * 24 * 60 * 60 * 1000;

    tx.set(modRef, { strikes, lastAt: admin.firestore.FieldValue.serverTimestamp() }, { merge: true });
    tx.set(db.doc(`bans/${reportedUid}`), {
      until, days, strikes, reason,
      createdAt: admin.firestore.FieldValue.serverTimestamp()
    });
    // نعلّم الرسائل المخالفة حتى ما تنعاقب مرة ثانية ببلاغ ثاني
    msgRefs.forEach((r) => tx.update(r, { punished: true }));
    return { strikes, days };
  });
}

// بلاغ جديد: نتحقق من الرسائل الحقيقية (مو من كلام المبلّغ) ثم نقرر
exports.onReportCreated = onDocumentCreated('reports/{id}', async (event) => {
  const ref = event.data.ref;
  const r = event.data.data();
  const parts = String(r.chatId || '').split('_');

  if (parts.length !== 2 || !parts.includes(r.reporter) || !parts.includes(r.reported)) {
    return ref.update({ status: 'invalid' });
  }

  const ids = Array.isArray(r.messageIds) ? r.messageIds.slice(0, 15) : [];
  const refs = ids.map((id) => db.doc(`chats/${r.chatId}/messages/${id}`));
  const snaps = refs.length ? await db.getAll(...refs) : [];

  const bad = [];
  let category = null;
  let needsReview = false;
  snaps.forEach((s) => {
    if (!s.exists) return;
    const d = s.data();
    if (d.from !== r.reported || d.punished) return;   // لازم تكون رسالة المبلّغ عنه فعلاً وما انعوقب عليها
    const c = detect(d.text);
    if (c === 'selfHarm') needsReview = true;
    else if (c) { bad.push(s.ref); category = category || c; }
  });

  if (bad.length > 0) {
    const out = await applyViolation(r.reported, bad, category);
    return ref.update({ status: 'confirmed', category, strikes: out.strikes, banDays: out.days, auto: true });
  }
  // ما ثبت تلقائياً: ينتظر مراجعة يدوية (غيّر status إلى confirmed من Firebase Console لتطبيق العقوبة)
  return ref.update({ status: needsReview ? 'review_wellbeing' : 'pending_review' });
});

// مراجعة يدوية: إذا غيّرت status إلى "confirmed" من Console تنطبق العقوبة
exports.onReportReviewed = onDocumentUpdated('reports/{id}', async (event) => {
  const before = event.data.before.data();
  const after = event.data.after.data();
  if (before.status === after.status || after.status !== 'confirmed' || after.auto) return;

  const ids = Array.isArray(after.messageIds) ? after.messageIds.slice(0, 15) : [];
  const refs = ids.map((id) => db.doc(`chats/${after.chatId}/messages/${id}`));
  const snaps = refs.length ? await db.getAll(...refs) : [];
  const msgRefs = snaps.filter((s) => s.exists && s.get('from') === after.reported && !s.get('punished')).map((s) => s.ref);

  const out = await applyViolation(after.reported, msgRefs, after.reason);
  return event.data.after.ref.update({ auto: false, strikes: out.strikes, banDays: out.days });
});
