// End-to-end test of the TESADÜF database layer on PGlite (Postgres in WASM).
//
//   npm i @electric-sql/pglite@0.3.7
//   node supabase/tests/pglite_flow_test.mjs
//
// Supabase's auth schema is stubbed: auth.uid() reads request.jwt.claims.sub,
// exactly like on Supabase. Each "user" call runs as the `authenticated` role so
// RLS is enforced; time travel is done as superuser by shifting timestamps.

import { PGlite } from "@electric-sql/pglite";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const migration = readFileSync(
  process.env.TESADUF_MIGRATION ?? join(here, "..", "migrations", "20261008000000_tesaduf_init.sql"),
  "utf8",
);

const db = new PGlite();
let passed = 0;
let failed = 0;

function check(name, cond, detail = "") {
  if (cond) {
    passed++;
    console.log(`  PASS  ${name}`);
  } else {
    failed++;
    console.log(`  FAIL  ${name} ${detail}`);
  }
}

await db.exec(`
  create role anon nologin;
  create role authenticated nologin;
  create schema auth;
  create table auth.users (id uuid primary key);
  create function auth.uid() returns uuid language sql stable as $$
    select nullif(current_setting('request.jwt.claims', true)::jsonb ->> 'sub', '')::uuid
  $$;
  grant usage on schema auth to anon, authenticated;
  grant execute on function auth.uid() to anon, authenticated;
  grant usage on schema public to anon, authenticated;
`);
await db.exec(migration);

const users = {};
async function newUser(name) {
  const id = crypto.randomUUID();
  await db.query("insert into auth.users(id) values ($1)", [id]);
  users[name] = id;
  return id;
}

// Run SQL as a signed-in user (role authenticated, RLS on). Returns rows or throws.
async function as(name, sql, params = []) {
  const claims = name ? JSON.stringify({ sub: users[name], role: "authenticated" }) : "";
  await db.query("select set_config('request.jwt.claims', $1, false)", [claims]);
  await db.exec(`set role ${name ? "authenticated" : "anon"}`);
  try {
    return (await db.query(sql, params)).rows;
  } finally {
    await db.exec("reset role");
  }
}
async function rpc(name, sql, params = []) {
  const rows = await as(name, sql, params);
  return Object.values(rows[0])[0];
}
async function rpcError(name, sql, params = []) {
  try {
    await as(name, sql, params);
    return null;
  } catch (e) {
    return e.message;
  }
}
async function shiftMatch(matchId, interval) {
  await db.query(
    `update public.matches set started_at = started_at - $2::interval,
       expires_at = expires_at - $2::interval, decision_deadline = decision_deadline - $2::interval
     where id = $1`,
    [matchId, interval],
  );
}

for (const n of ["ali", "ayse", "can", "deniz", "ece"]) await newUser(n);

console.log("\n# Profiles / anonymous id");
const ali1 = await rpc("ali", "select public.ensure_profile()");
const ali2 = await rpc("ali", "select public.ensure_profile()");
check("anonymous id format", /^[A-HJ-NP-Z2-9]{8}$/.test(ali1.profile.anonymous_id), ali1.profile.anonymous_id);
check("anonymous id stable across calls", ali1.profile.anonymous_id === ali2.profile.anonymous_id);
check("avatar assigned", /^orb_[1-8]$/.test(ali1.profile.avatar));
for (const n of ["ayse", "can", "deniz", "ece"]) await rpc(n, "select public.ensure_profile()");
const ids = new Set();
for (const n of Object.keys(users)) {
  ids.add((await as(n, "select anonymous_id from public.profiles"))[0].anonymous_id);
}
check("anonymous ids unique", ids.size === 5);
check("anon role gets NOT_AUTHENTICATED",
  (await rpcError(null, "select public.ensure_profile()"))?.includes("permission denied") ||
  (await rpcError(null, "select public.ensure_profile()"))?.includes("NOT_AUTHENTICATED"));
check("profiles RLS: only own row visible", (await as("ali", "select * from public.profiles")).length === 1);
check("cannot update own anonymous_id directly",
  (await rpcError("ali", "update public.profiles set anonymous_id='AAAAAAAA'"))?.includes("permission denied"));
check("cannot call internal helper",
  (await rpcError("ali", "select public._generate_anonymous_id()"))?.includes("permission denied"));

console.log("\n# Matchmaking");
const w1 = await rpc("ali", "select public.find_match('text')");
check("first user waits", w1.state === "waiting", JSON.stringify(w1));
check("voice mode rejected", (await rpcError("ali", "select public.find_match('voice')"))?.includes("UNSUPPORTED_MODE"));
const m1 = await rpc("ayse", "select public.find_match('text')");
check("second user matched", m1.state === "matched", JSON.stringify(m1));
const matchId = m1.match.id;
check("partner id is the anonymous id", m1.match.partner.anonymous_id === ali1.profile.anonymous_id);
check("partner uuid not exposed", !JSON.stringify(m1).includes(users.ali));
const expiresMs = new Date(m1.match.expires_at) - new Date(m1.match.started_at);
check("match lasts 15 minutes (server side)", expiresMs === 15 * 60 * 1000, String(expiresMs));
const a1 = await rpc("ali", "select public.find_match('text')");
check("waiting user picks up the same match", a1.state === "matched" && a1.match.id === matchId);
const c1 = await rpc("can", "select public.find_match('text')");
check("users in a live match are not matched again", c1.state === "waiting");
check("match RLS: outsider sees nothing",
  (await as("can", "select * from public.matches where id=$1", [matchId])).length === 0);
check("get_match: outsider gets MATCH_NOT_FOUND",
  (await rpcError("can", "select public.get_match($1)", [matchId]))?.includes("MATCH_NOT_FOUND"));

console.log("\n# Messages");
const cid = crypto.randomUUID();
const s1 = await rpc("ali", "select public.send_message($1,$2,$3)", [matchId, "  Merhaba!  ", cid]);
check("message stored and trimmed", s1.body === "Merhaba!" && s1.mine === true);
const s1b = await rpc("ali", "select public.send_message($1,$2,$3)", [matchId, "Merhaba!", cid]);
check("duplicate send is idempotent", s1b.id === s1.id);
await rpc("ayse", "select public.send_message($1,$2,$3)", [matchId, "Selam :)", crypto.randomUUID()]);
const l1 = await rpc("ayse", "select public.list_messages($1, 0, 50)", [matchId]);
check("list returns 2 messages in order", l1.messages.length === 2 && l1.messages[0].body === "Merhaba!");
check("mine flag per viewer", l1.messages[0].mine === false && l1.messages[1].mine === true);
const l2 = await rpc("ayse", "select public.list_messages($1, $2, 50)", [matchId, l1.messages[0].id]);
check("after-cursor returns only newer", l2.messages.length === 1);
check("messages RLS: participant can select table",
  (await as("ayse", "select * from public.messages where match_id=$1", [matchId])).length === 2);
check("messages RLS: outsider sees nothing",
  (await as("can", "select * from public.messages")).length === 0);
check("outsider cannot send", (await rpcError("can", "select public.send_message($1,'x',$2)", [matchId, crypto.randomUUID()]))?.includes("MATCH_NOT_FOUND"));
check("empty message rejected", (await rpcError("ali", "select public.send_message($1,'   ',$2)", [matchId, crypto.randomUUID()]))?.includes("INVALID_MESSAGE"));
check("too long message rejected", (await rpcError("ali", "select public.send_message($1,$2,$3)", [matchId, "x".repeat(1001), crypto.randomUUID()]))?.includes("INVALID_MESSAGE"));
check("direct insert blocked", (await rpcError("ali", "insert into public.messages(match_id,sender_id,client_id,body) values ($1,$2,gen_random_uuid(),'x')", [matchId, users.ali]))?.includes("permission denied"));
check("decision before expiry rejected", (await rpcError("ali", "select public.submit_decision($1,'continue')", [matchId]))?.includes("MATCH_NOT_EXPIRED"));

let rl = null;
for (let i = 0; i < 20 && !rl; i++) {
  rl = await rpcError("ali", "select public.send_message($1,$2,$3)", [matchId, `spam ${i}`, crypto.randomUUID()]);
}
check("rate limit kicks in", rl?.includes("RATE_LIMITED"), String(rl));
await db.exec("update public.messages set created_at = created_at - interval '1 minute'");

console.log("\n# 15 minute expiry + destiny");
await shiftMatch(matchId, "15 minutes 1 second");
const g1 = await rpc("ali", "select public.get_match($1)", [matchId]);
check("expired match moves to deciding", g1.status === "deciding");
check("sending after expiry rejected", (await rpcError("ali", "select public.send_message($1,'late',$2)", [matchId, crypto.randomUUID()]))?.includes("MATCH_NOT_OPEN"));
check("messages still readable while deciding", (await rpc("ali", "select public.list_messages($1,0,50)", [matchId])).messages.length >= 2);
const d1 = await rpc("ali", "select public.submit_decision($1,'continue')", [matchId]);
check("one continue keeps deciding", d1.status === "deciding" && d1.my_decision === "continue");
const d1b = await rpc("ali", "select public.submit_decision($1,'end')", [matchId]);
check("first decision is final (cannot flip)", d1b.my_decision === "continue");
const pv = await rpc("ayse", "select public.get_match($1)", [matchId]);
check("partner sees that a decision was made (not which)", pv.partner_decided === true && pv.my_decision === null);
const d2 = await rpc("ayse", "select public.submit_decision($1,'continue')", [matchId]);
check("both continue => destiny", d2.status === "destiny" && d2.destiny_at);
const d3 = await rpc("ali", "select public.get_match($1)", [matchId]);
check("destiny visible to other side", d3.status === "destiny");
const s2 = await rpc("ali", "select public.send_message($1,'Kader!',$2)", [matchId, crypto.randomUUID()]);
check("destiny chat has no time limit", s2.body === "Kader!");
await rpc("can", "select public.cancel_matchmaking()");
const free = await rpc("ali", "select public.find_match('text')");
check("destiny chat does not block new tesadüf", free.state === "waiting");
await rpc("ali", "select public.cancel_matchmaking()");

console.log("\n# One side ends => match ends");
await rpc("can", "select public.cancel_matchmaking()");
await rpc("can", "select public.find_match('text')");
const m2 = await rpc("deniz", "select public.find_match('text')");
check("can & deniz matched", m2.state === "matched");
await shiftMatch(m2.match.id, "16 minutes");
await rpc("can", "select public.submit_decision($1,'continue')", [m2.match.id]);
const e2 = await rpc("deniz", "select public.submit_decision($1,'end')", [m2.match.id]);
check("continue + end => ended/declined", e2.status === "ended" && e2.end_reason === "declined");
check("ended chat messages hidden", (await rpc("can", "select public.list_messages($1,0,50)", [m2.match.id])).messages.length === 0);

console.log("\n# Decision deadline");
const m3a = await rpc("can", "select public.find_match('text')");
const m3 = await rpc("ece", "select public.find_match('text')");
check("can & ece matched (previous partners avoided)", m3.state === "matched" && m3a.state === "waiting");
await shiftMatch(m3.match.id, "18 minutes");
const e3 = await rpc("can", "select public.get_match($1)", [m3.match.id]);
check("no decisions after deadline => ended/expired", e3.status === "ended" && e3.end_reason === "expired");

console.log("\n# End match / block / report");
await rpc("can", "select public.find_match('text')");
const m4 = await rpc("deniz", "select public.find_match('text')");
check("rematch allowed when no one else waits", m4.state === "matched");
const b4 = await rpc("deniz", "select public.block_user($1)", [m4.match.id]);
check("block ends match", b4.blocked && b4.match.status === "ended" && b4.match.end_reason === "blocked");
await rpc("can", "select public.find_match('text')");
const m5 = await rpc("deniz", "select public.find_match('text')");
check("blocked pair is never matched again", m5.state === "waiting");
check("blocker sees own block row", (await as("deniz", "select * from public.blocks")).length === 1);
check("blocked user does not see block row", (await as("can", "select * from public.blocks")).length === 0);
await rpc("deniz", "select public.cancel_matchmaking()");

const m6 = await rpc("ece", "select public.find_match('text')");
check("can & ece matched again", m6.state === "matched");
check("invalid report reason rejected", (await rpcError("ece", "select public.report_user($1,'bad')", [m6.match.id]))?.includes("INVALID_REASON"));
const r6 = await rpc("ece", "select public.report_user($1,'harassment','kaba davrandı')", [m6.match.id]);
check("report ends match and blocks", r6.reported && r6.match.status === "ended" && r6.match.end_reason === "reported");
const reportRows = (await db.query("select * from public.reports")).rows;
check("report row stored", reportRows.length === 1 && reportRows[0].reason === "harassment");
check("reports not readable via API", (await rpcError("ece", "select * from public.reports"))?.includes("permission denied"));
const r6b = await rpc("ece", "select public.report_user($1,'spam')", [m6.match.id]);
check("duplicate report is ignored", r6b.reported && (await db.query("select count(*)::int c from public.reports")).rows[0].c === 1);

console.log("\n# End match");
await rpc("ali", "select public.find_match('text')");
const m7 = await rpc("ece", "select public.find_match('text')");
const e7 = await rpc("ali", "select public.end_match($1)", [m7.match.id]);
check("end_match ends", e7.status === "ended" && e7.end_reason === "ended_by_user" && e7.ended_by_me);
const e7b = await rpc("ece", "select public.get_match($1)", [m7.match.id]);
check("partner sees ended", e7b.status === "ended" && e7b.ended_by_me === false);

console.log("\n# Abandonment");
await rpc("ali", "select public.find_match('text')");
const m8 = await rpc("ece", "select public.find_match('text')");
await shiftMatch(m8.match.id, "4 minutes");
await db.query("update public.profiles set last_seen_at = now() - interval '4 minutes' where id = $1", [users.ece]);
const e8 = await rpc("ali", "select public.heartbeat($1)", [m8.match.id]);
check("silent partner => abandoned", e8.match.status === "ended" && e8.match.end_reason === "abandoned");

console.log("\n# History (my-chats)");
const h = await rpc("ali", "select public.my_chats(30)");
check("history lists matches", h.length >= 3);
check("history marks destiny", h.some((x) => x.is_destiny && x.status === "destiny"));
check("history has no uuids of partners", !JSON.stringify(h).includes(users.ayse));

console.log("\n# Suspended account");
await db.query("update public.profiles set status='suspended' where id=$1", [users.deniz]);
check("suspended user cannot match", (await rpcError("deniz", "select public.find_match('text')"))?.includes("ACCOUNT_SUSPENDED"));

console.log(`\n${passed} passed, ${failed} failed`);
process.exit(failed ? 1 : 0);
