import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });

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

    const admin = createClient(url, serviceKey, {
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data: profile, error: profileError } = await admin.schema("edulock")
      .from("user_profiles").select("account_role").eq("user_id", authData.user.id).maybeSingle();
    if (profileError || profile?.account_role !== "child") {
      return json({ error: "Only a linked child account can request its daily tasks" }, 403);
    }
    const { data: result, error } = await admin.schema("edulock")
      .rpc("ensure_daily_tasks_for_child", { p_user_id: authData.user.id });
    if (error || !result) {
      console.error("Daily task generation failed", error?.code, error?.message);
      return json({ error: "Daily tasks could not be created. Check the child's subjects and available questions." }, 400);
    }
    return json(result);
  } catch (error) {
    console.error("Ensure daily tasks failed", error);
    return json({ error: "Daily tasks could not be generated" }, 500);
  }
});
