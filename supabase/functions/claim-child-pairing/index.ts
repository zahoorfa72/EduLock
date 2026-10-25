import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });

async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);
  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !anonKey || !serviceKey) return json({ error: "Server configuration is incomplete" }, 500);
  const authorization = req.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) return json({ error: "Sign in as the child account first" }, 401);

  try {
    const caller = createClient(url, anonKey, {
      global: { headers: { Authorization: authorization } },
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data: authData, error: authError } = await caller.auth.getUser();
    if (authError || !authData.user) return json({ error: "Session expired; sign in again" }, 401);

    const body = await req.json();
    const code = typeof body.code === "string" ? body.code.replace(/\D/g, "") : "";
    if (!/^\d{6}$/.test(code)) return json({ error: "Enter the six-digit parent pairing code" }, 400);

    const { data: role, error: roleError } = await createClient(url, serviceKey, {
      auth: { persistSession: false, autoRefreshToken: false },
    }).schema("edulock").from("user_profiles")
      .select("account_role").eq("user_id", authData.user.id).maybeSingle();
    if (roleError || role?.account_role !== "child") return json({ error: "Use a child account to link this device" }, 403);

    const admin = createClient(url, serviceKey, {
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data: withinLimit, error: rateError } = await admin.schema("edulock").rpc("consume_pairing_attempt", {
      p_user_id: authData.user.id,
    });
    if (rateError || withinLimit !== true) return json({ error: "Too many pairing attempts. Try again in 15 minutes." }, 429);

    const { data: childId, error: claimError } = await admin.schema("edulock").rpc("claim_child_pairing", {
      p_code_hash: await sha256(code),
      p_user_id: authData.user.id,
    });
    if (claimError || !childId) {
      const message = claimError?.message?.includes("expired") || claimError?.message?.includes("invalid")
        ? "Pairing code is invalid or expired"
        : claimError?.message?.includes("already linked")
        ? "This child profile is already linked"
        : "Could not link this child account";
      return json({ error: message }, 400);
    }
    return json({ child_id: childId });
  } catch (error) {
    console.error("Claim pairing failed", error);
    return json({ error: "Could not link this child account" }, 500);
  }
});
