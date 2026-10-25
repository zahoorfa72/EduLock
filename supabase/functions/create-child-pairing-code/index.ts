import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { ...cors, "Content-Type": "application/json" },
  });

async function sha256(value: string): Promise<string> {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
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
  if (!authorization?.startsWith("Bearer ")) return json({ error: "Sign in first" }, 401);

  try {
    const caller = createClient(url, anonKey, {
      global: { headers: { Authorization: authorization } },
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data: authData, error: authError } = await caller.auth.getUser();
    if (authError || !authData.user) return json({ error: "Session expired; sign in again" }, 401);

    const body = await req.json();
    const childId = typeof body.child_id === "string" ? body.child_id : "";
    if (!/^[0-9a-f]{8}-[0-9a-f-]{27}$/i.test(childId)) return json({ error: "A valid child profile is required" }, 400);

    const admin = createClient(url, serviceKey, {
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data: role, error: roleError } = await admin.schema("edulock")
      .from("user_profiles").select("account_role").eq("user_id", authData.user.id).maybeSingle();
    if (roleError || role?.account_role !== "parent") return json({ error: "Parent access is required" }, 403);

    const { data: child, error: childError } = await admin.schema("edulock")
      .from("children").select("id,family_id,child_user_id").eq("id", childId).maybeSingle();
    if (childError || !child) return json({ error: "Child profile was not found" }, 404);
    if (child.child_user_id) return json({ error: "This child profile is already linked" }, 409);

    const { data: family, error: familyError } = await admin.schema("edulock")
      .from("family_profiles").select("id,owner_user_id").eq("id", child.family_id).maybeSingle();
    if (familyError || family?.owner_user_id !== authData.user.id) {
      return json({ error: "You cannot pair a child outside your family" }, 403);
    }

    // Issuing a replacement invalidates any earlier, still-unused code for this child.
    const { error: invalidateError } = await admin.schema("edulock").from("child_pairing_codes")
      .update({ consumed_at: new Date().toISOString() })
      .eq("child_id", child.id)
      .is("consumed_at", null);
    if (invalidateError) return json({ error: "Could not rotate the pairing code" }, 500);

    // The code is shown only once. Only its SHA-256 digest is stored.
    for (let tries = 0; tries < 5; tries++) {
      const value = crypto.getRandomValues(new Uint32Array(1))[0] % 1000000;
      const code = String(value).padStart(6, "0");
      const codeHash = await sha256(code);
      const { error: insertError } = await admin.schema("edulock").from("child_pairing_codes").insert({
        child_id: child.id,
        family_id: child.family_id,
        created_by: authData.user.id,
        code_hash: codeHash,
        expires_at: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
      });
      if (!insertError) return json({ code, expires_in_seconds: 600 });
      if (insertError.code !== "23505") {
        console.error("Pairing-code insert failed", insertError.code);
        return json({ error: "Could not create a pairing code. Check the EduLock schema is exposed in Supabase." }, 500);
      }
    }
    return json({ error: "Could not generate a unique code; try again" }, 500);
  } catch (error) {
    console.error("Pairing function failed", error);
    return json({ error: "Could not create a pairing code" }, 500);
  }
});
