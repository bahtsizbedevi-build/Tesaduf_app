// End-to-end test against the REAL Supabase project, through the deployed Edge Functions,
// exactly like the Android app talks to it.
//
//   SUPABASE_URL=https://<ref>.supabase.co SUPABASE_ANON_KEY=<publishable> node supabase/tests/e2e_remote_test.mjs
//
// Creates throw-away anonymous users. Time-based transitions (15 min expiry, decision
// window) cannot be fast-forwarded on a live server; they are covered by pglite_flow_test.mjs.

const URL = process.env.SUPABASE_URL;
const KEY = process.env.SUPABASE_ANON_KEY;
if (!URL || !KEY) throw new Error("SUPABASE_URL and SUPABASE_ANON_KEY are required");

let passed = 0;
let failed = 0;
function check(name, cond, detail = "") {
  if (cond) { passed++; console.log(`  PASS  ${name}`); }
  else { failed++; console.log(`  FAIL  ${name} ${detail}`); }
}

async function signUp() {
  const r = await fetch(`${URL}/auth/v1/signup`, {
    method: "POST", headers: { apikey: KEY, "Content-Type": "application/json" }, body: "{}",
  });
  const j = await r.json();
  if (!j.access_token) throw new Error(`anonymous sign-up failed: ${JSON.stringify(j)}`);
  return j.access_token;
}

async function fn(token, name, body = {}) {
  const r = await fetch(`${URL}/functions/v1/${name}`, {
    method: "POST",
    headers: { apikey: KEY, Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const j = await r.json().catch(() => ({}));
  return { status: r.status, ...j };
}

async function rest(token, path) {
  const r = await fetch(`${URL}/rest/v1/${path}`, { headers: { apikey: KEY, Authorization: `Bearer ${token}` } });
  return { status: r.status, body: await r.json().catch(() => null) };
}

const t0 = Date.now();
console.log("# Anonymous auth + bootstrap");
const [a, b, c] = [await signUp(), await signUp(), await signUp()];
const pa = await fn(a, "bootstrap");
const pa2 = await fn(a, "bootstrap");
const pb = await fn(b, "bootstrap");
await fn(c, "bootstrap");
check("bootstrap ok", pa.ok && pb.ok, JSON.stringify(pa));
check("anonymous id format", /^[A-HJ-NP-Z2-9]{8}$/.test(pa.data?.profile?.anonymous_id ?? ""));
check("anonymous id stable", pa.data?.profile?.anonymous_id === pa2.data?.profile?.anonymous_id);
check("server_time present", typeof pa.server_time === "string");
check("no token => 401", (await fn("x", "bootstrap")).status === 401);

console.log("\n# Matchmaking");
// Leave any queue from previous runs.
for (const t of [a, b, c]) await fn(t, "matchmaker", { action: "cancel" });
const w = await fn(a, "matchmaker", { action: "join" });
check("first user waits", w.data?.state === "waiting", JSON.stringify(w));
const m = await fn(b, "matchmaker", { action: "join" });
check("second user matched", m.data?.state === "matched", JSON.stringify(m));
const match = m.data?.match;
const matchId = match?.id;
check("partner shown by anonymous id", match?.partner?.anonymous_id === pa.data?.profile?.anonymous_id);
check("15 min expiry set by server",
  Math.abs(Date.parse(match.expires_at) - Date.parse(match.started_at) - 15 * 60_000) < 1000);
const a2 = await fn(a, "matchmaker", { action: "join" });
check("waiting user gets same match", a2.data?.match?.id === matchId);
check("invalid action rejected", (await fn(a, "matchmaker", { action: "boom" })).status === 400);

console.log("\n# Messages");
const cid = crypto.randomUUID();
const s1 = await fn(a, "send-message", { match_id: matchId, body: "Merhaba!", client_id: cid });
const s1dup = await fn(a, "send-message", { match_id: matchId, body: "Merhaba!", client_id: cid });
check("send ok", s1.ok && s1.data?.mine === true, JSON.stringify(s1));
check("retry is idempotent", s1dup.data?.id === s1.data?.id);
await fn(b, "send-message", { match_id: matchId, body: "Selam :)", client_id: crypto.randomUUID() });
const lb = await fn(b, "messages", { match_id: matchId, after: 0 });
check("partner reads both messages", lb.data?.messages?.length === 2, JSON.stringify(lb.data?.messages));
check("mine flag per viewer", lb.data?.messages?.[0]?.mine === false && lb.data?.messages?.[1]?.mine === true);
check("empty message rejected", (await fn(a, "send-message", { match_id: matchId, body: "  ", client_id: crypto.randomUUID() })).status === 400);
check("bad uuid rejected", (await fn(a, "send-message", { match_id: "nope", body: "x", client_id: crypto.randomUUID() })).status === 400);

console.log("\n# Isolation (RLS + function authorization)");
check("outsider cannot read via function", (await fn(c, "messages", { match_id: matchId })).status === 404);
check("outsider cannot send", (await fn(c, "send-message", { match_id: matchId, body: "x", client_id: crypto.randomUUID() })).status === 404);
const rc = await rest(c, `messages?match_id=eq.${matchId}`);
check("outsider REST read of messages returns nothing", rc.status === 200 && rc.body.length === 0, JSON.stringify(rc));
const rm = await rest(c, `matches?id=eq.${matchId}`);
check("outsider REST read of match returns nothing", rm.body?.length === 0);
const rp = await rest(a, "profiles?select=*");
check("profiles: only own row", rp.body?.length === 1);
const rq = await rest(a, "reports?select=*");
check("reports not readable", rq.status >= 400);
const ins = await fetch(`${URL}/rest/v1/messages`, {
  method: "POST",
  headers: { apikey: KEY, Authorization: `Bearer ${a}`, "Content-Type": "application/json" },
  body: JSON.stringify({ match_id: matchId, sender_id: "00000000-0000-0000-0000-000000000000", client_id: crypto.randomUUID(), body: "x" }),
});
check("direct table insert blocked", ins.status >= 400);
check("partner uuid never in API payloads", !JSON.stringify(lb).match(/"(user_a|user_b|sender_id)"/));

console.log("\n# Heartbeat / status / decision guard");
const hb = await fn(a, "heartbeat", { match_id: matchId });
check("heartbeat returns match + partner online", hb.data?.match?.partner?.online === true, JSON.stringify(hb));
const st = await fn(b, "match-status", { match_id: matchId });
check("status active", st.data?.status === "active");
check("decision before expiry rejected", (await fn(a, "destiny-decision", { match_id: matchId, decision: "continue" })).status === 409);

console.log("\n# Block => never matched again");
const bl = await fn(b, "block-user", { match_id: matchId });
check("block ends match", bl.data?.blocked && bl.data?.match?.status === "ended" && bl.data?.match?.end_reason === "blocked");
check("send after end rejected", (await fn(a, "send-message", { match_id: matchId, body: "x", client_id: crypto.randomUUID() })).status === 409);
check("ended chat messages hidden", (await fn(a, "messages", { match_id: matchId })).data?.messages?.length === 0);
await fn(a, "matchmaker", { action: "join" });
const again = await fn(b, "matchmaker", { action: "join" });
check("blocked pair not rematched", again.data?.state === "waiting", JSON.stringify(again.data));

console.log("\n# Report");
const m2 = await fn(c, "matchmaker", { action: "join" });
check("third user matched with a waiting user", m2.data?.state === "matched", JSON.stringify(m2.data));
const id2 = m2.data?.match?.id;
check("bad reason rejected", (await fn(c, "report-user", { match_id: id2, reason: "bad" })).status === 400);
const rep = await fn(c, "report-user", { match_id: id2, reason: "harassment" });
check("report stored + ends match", rep.data?.reported && rep.data?.match?.end_reason === "reported", JSON.stringify(rep));

console.log("\n# End match + history");
for (const t of [a, b, c]) await fn(t, "matchmaker", { action: "cancel" });
const d = await signUp();
await fn(d, "bootstrap");
await fn(d, "matchmaker", { action: "join" });
const m3 = await fn(a, "matchmaker", { action: "join" });
const id3 = m3.data?.match?.id;
check("new match for end test", m3.data?.state === "matched", JSON.stringify(m3.data));
const e = await fn(d, "end-match", { match_id: id3 });
check("end-match ends", e.data?.status === "ended" && e.data?.ended_by_me === true);
const eb = await fn(a, "match-status", { match_id: id3 });
check("partner sees ended (not by me)", eb.data?.status === "ended" && eb.data?.ended_by_me === false);
const h = await fn(a, "my-chats", {});
check("history lists past tesadüfs", Array.isArray(h.data) && h.data.length >= 2, JSON.stringify(h).slice(0, 200));
for (const t of [a, b, c, d]) await fn(t, "matchmaker", { action: "cancel" });

console.log(`\n${passed} passed, ${failed} failed (${((Date.now() - t0) / 1000).toFixed(1)} s)`);
process.exit(failed ? 1 : 0);
