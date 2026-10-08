// Verifies Supabase Realtime delivers message inserts to a match participant (and only to
// participants), using the same Phoenix v1 join payload as the Android RealtimeClient.
//
//   SUPABASE_URL=... SUPABASE_ANON_KEY=... node supabase/tests/realtime_test.mjs
const URL = process.env.SUPABASE_URL;
const KEY = process.env.SUPABASE_ANON_KEY;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function signUp() {
  const j = await (await fetch(`${URL}/auth/v1/signup`, {
    method: "POST", headers: { apikey: KEY, "Content-Type": "application/json" }, body: "{}",
  })).json();
  return j.access_token;
}
const fn = async (t, name, body = {}) => (await fetch(`${URL}/functions/v1/${name}`, {
  method: "POST", headers: { apikey: KEY, Authorization: `Bearer ${t}`, "Content-Type": "application/json" },
  body: JSON.stringify(body),
})).json();

function subscribe(token, matchId) {
  const events = [];
  let joined = false;
  const ws = new WebSocket(`${URL.replace("https://", "wss://")}/realtime/v1/websocket?apikey=${KEY}&vsn=1.0.0`);
  const topic = `realtime:tesaduf-match-${matchId}`;
  ws.onopen = () => ws.send(JSON.stringify({
    topic, event: "phx_join", ref: "1", join_ref: "1",
    payload: {
      access_token: token,
      config: {
        private: false, broadcast: { self: false }, presence: { key: "" },
        postgres_changes: [
          { event: "INSERT", schema: "public", table: "messages", filter: `match_id=eq.${matchId}` },
          { event: "UPDATE", schema: "public", table: "matches", filter: `id=eq.${matchId}` },
        ],
      },
    },
  }));
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data);
    if (m.event === "phx_reply" && m.ref === "1") joined = m.payload?.status === "ok";
    if (m.event === "postgres_changes") events.push(m.payload?.data?.table);
    if (m.event === "system") console.log("  system:", m.payload?.status, m.payload?.message);
  };
  return { events, isJoined: () => joined, close: () => ws.close() };
}

const [a, b, c] = [await signUp(), await signUp(), await signUp()];
for (const t of [a, b, c]) await fn(t, "bootstrap");
await fn(a, "matchmaker", { action: "join" });
const m = await fn(b, "matchmaker", { action: "join" });
const matchId = m.data?.match?.id;
if (!matchId) throw new Error("no match: " + JSON.stringify(m));

const subA = subscribe(a, matchId);
const subC = subscribe(c, matchId); // outsider
await sleep(4000);
console.log("participant joined:", subA.isJoined());
await fn(b, "send-message", { match_id: matchId, body: "realtime?", client_id: crypto.randomUUID() });
await sleep(4000);
console.log("participant events:", JSON.stringify(subA.events));
console.log("outsider events:", JSON.stringify(subC.events));
const ok = subA.isJoined() && subA.events.includes("messages") && !subC.events.includes("messages");
await fn(a, "end-match", { match_id: matchId });
subA.close(); subC.close();
console.log(ok ? "REALTIME: PASS" : "REALTIME: FAIL");
process.exit(ok ? 0 : 1);
