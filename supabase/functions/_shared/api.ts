// Shared plumbing for all TESADÜF Edge Functions.
//
// Every function is a thin, validated wrapper around one SECURITY DEFINER RPC.
// The RPC is executed with the *caller's* JWT (anon key + Authorization header),
// so PostgREST verifies the token and auth.uid() identifies the user. No
// service-role key is used anywhere.
import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2.49.4";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

export class ApiError extends Error {
  constructor(readonly status: number, readonly code: string, message?: string) {
    super(message ?? code);
  }
}

// RPC error codes (raised with `raise exception 'CODE'`) -> HTTP status.
const rpcErrorStatus: Record<string, number> = {
  NOT_AUTHENTICATED: 401,
  ACCOUNT_SUSPENDED: 403,
  PROFILE_NOT_FOUND: 404,
  MATCH_NOT_FOUND: 404,
  MATCH_NOT_OPEN: 409,
  MATCH_NOT_EXPIRED: 409,
  RATE_LIMITED: 429,
  UNSUPPORTED_MODE: 400,
  INVALID_MATCH_ID: 400,
  INVALID_DECISION: 400,
  INVALID_MESSAGE: 400,
  INVALID_CLIENT_ID: 400,
  INVALID_REASON: 400,
  INVALID_DETAILS: 400,
};

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json; charset=utf-8" },
  });
}

export type Body = Record<string, unknown>;

export interface Context {
  body: Body;
  rpc: <T = unknown>(fn: string, args?: Record<string, unknown>) => Promise<T>;
}

function userClient(authHeader: string): SupabaseClient {
  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  if (!url || !anonKey) throw new ApiError(500, "SERVER_MISCONFIGURED");
  return createClient(url, anonKey, {
    global: { headers: { Authorization: authHeader } },
    auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
  });
}

export function serve(name: string, run: (ctx: Context) => Promise<unknown>): void {
  Deno.serve(async (req) => {
    if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
    if (req.method !== "POST") {
      return json(405, { ok: false, error: { code: "METHOD_NOT_ALLOWED" } });
    }

    try {
      const authHeader = req.headers.get("Authorization") ?? "";
      if (!/^Bearer\s+\S+$/.test(authHeader)) throw new ApiError(401, "NOT_AUTHENTICATED");

      let body: Body = {};
      const text = await req.text();
      if (text.length > 8_192) throw new ApiError(413, "PAYLOAD_TOO_LARGE");
      if (text.trim().length > 0) {
        try {
          const parsed = JSON.parse(text);
          if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
            throw new Error("not an object");
          }
          body = parsed as Body;
        } catch {
          throw new ApiError(400, "INVALID_JSON");
        }
      }

      const client = userClient(authHeader);
      const rpc = async <T>(fn: string, args: Record<string, unknown> = {}): Promise<T> => {
        const { data, error } = await client.rpc(fn, args);
        if (error) {
          const code = error.message?.trim() ?? "";
          if (code in rpcErrorStatus) throw new ApiError(rpcErrorStatus[code], code);
          // PostgREST rejects expired / invalid JWTs with PGRST301/PGRST302 (HTTP 401).
          if (error.code?.startsWith("PGRST30") || /jwt/i.test(code)) {
            throw new ApiError(401, "TOKEN_INVALID");
          }
          console.error(`[${name}] rpc ${fn} failed`, error.code, error.message);
          throw new ApiError(500, "INTERNAL");
        }
        return data as T;
      };

      const data = await run({ body, rpc });
      return json(200, { ok: true, data, server_time: new Date().toISOString() });
    } catch (e) {
      if (e instanceof ApiError) {
        return json(e.status, {
          ok: false,
          error: { code: e.code },
          server_time: new Date().toISOString(),
        });
      }
      console.error(`[${name}] unhandled`, e);
      return json(500, { ok: false, error: { code: "INTERNAL" } });
    }
  });
}

// ---------------------------------------------------------------------------
// Input validation
// ---------------------------------------------------------------------------
const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function requireUuid(body: Body, key: string, code = "INVALID_MATCH_ID"): string {
  const v = body[key];
  if (typeof v !== "string" || !uuidPattern.test(v)) throw new ApiError(400, code);
  return v.toLowerCase();
}

export function optionalUuid(body: Body, key: string): string | null {
  if (body[key] === undefined || body[key] === null) return null;
  return requireUuid(body, key);
}

export function requireEnum<T extends string>(body: Body, key: string, allowed: readonly T[], code: string): T {
  const v = body[key];
  if (typeof v !== "string" || !(allowed as readonly string[]).includes(v)) throw new ApiError(400, code);
  return v as T;
}

export function optionalInt(body: Body, key: string, min: number, max: number, fallback: number): number {
  const v = body[key];
  if (v === undefined || v === null) return fallback;
  if (typeof v !== "number" || !Number.isInteger(v) || v < min || v > max) {
    throw new ApiError(400, `INVALID_${key.toUpperCase()}`);
  }
  return v;
}

export function requireString(body: Body, key: string, maxLength: number, code: string): string {
  const v = body[key];
  if (typeof v !== "string") throw new ApiError(400, code);
  const trimmed = v.trim();
  if (trimmed.length === 0 || trimmed.length > maxLength) throw new ApiError(400, code);
  return trimmed;
}

export function optionalString(body: Body, key: string, maxLength: number, code: string): string | null {
  const v = body[key];
  if (v === undefined || v === null) return null;
  if (typeof v !== "string" || v.length > maxLength) throw new ApiError(400, code);
  const trimmed = v.trim();
  return trimmed.length === 0 ? null : trimmed;
}
