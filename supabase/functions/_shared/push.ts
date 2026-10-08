// FCM HTTP v1 sender. Uses the FIREBASE_SERVICE_ACCOUNT secret (set with
// `supabase secrets set`), never shipped to apps. If the secret is missing, pushes
// are silently skipped so chat keeps working.
import { createClient } from "npm:@supabase/supabase-js@2.49.4";

interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

let cachedToken: { value: string; expiresAt: number } | null = null;

function b64url(data: ArrayBuffer | string): string {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : new Uint8Array(data);
  let bin = "";
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function accessToken(sa: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken && cachedToken.expiresAt - 60 > now) return cachedToken.value;
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = b64url(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const pem = sa.private_key.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"],
  );
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claims}`));
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${header}.${claims}.${b64url(sig)}`,
  });
  const json = await res.json();
  if (!json.access_token) throw new Error("FCM auth failed");
  cachedToken = { value: json.access_token, expiresAt: now + (json.expires_in ?? 3600) };
  return json.access_token;
}

/** Notifies the recipient of message [messageId]. Never throws. */
export async function pushNewMessage(messageId: number): Promise<void> {
  try {
    const raw = Deno.env.get("FIREBASE_SERVICE_ACCOUNT");
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    const url = Deno.env.get("SUPABASE_URL");
    if (!raw || !serviceKey || !url) return;
    const sa = JSON.parse(raw) as ServiceAccount;
    const admin = createClient(url, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });
    const { data } = await admin.rpc("push_targets_for_message", { p_message_id: messageId });
    const target = data as { match_id: string; sender_id: string; body: string | null; tokens: string[] } | null;
    if (!target || target.tokens.length === 0) return;

    const token = await accessToken(sa);
    const dead: string[] = [];
    await Promise.all(target.tokens.map(async (deviceToken) => {
      // Data-only message: the app builds the notification itself (its own icon,
      // grouping, and no notification while that chat is open).
      const res = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({
          message: {
            token: deviceToken,
            android: { priority: "high", collapse_key: target.match_id },
            data: {
              type: "message",
              match_id: target.match_id,
              sender: target.sender_id,
              body: target.body ?? "",
            },
          },
        }),
      });
      if (res.status === 404 || res.status === 400) {
        const text = await res.text();
        if (/UNREGISTERED|INVALID_ARGUMENT/.test(text)) dead.push(deviceToken);
      } else {
        await res.body?.cancel();
      }
    }));
    if (dead.length) await admin.rpc("drop_push_tokens", { p_tokens: dead });
  } catch (e) {
    console.error("[push] failed", e instanceof Error ? e.message : e);
  }
}
