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

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !anonKey || !serviceKey) {
    return json({ error: "Server configuration is incomplete" }, 500);
  }

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
    const packageName = typeof body.package_name === "string" ? body.package_name.trim() : "";
    const minutes = Number(body.minutes);
    if (!/^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$/.test(packageName)) {
      return json({ error: "Choose a valid approved app" }, 400);
    }
    if (!Number.isInteger(minutes) || minutes < 1 || minutes > 1440) {
      return json({ error: "Choose a reward amount from 1 to 1440 minutes" }, 400);
    }

    const admin = createClient(url, serviceKey, {
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data, error } = await admin.schema("edulock").rpc("redeem_child_reward", {
      p_user_id: authData.user.id,
      p_package_name: packageName,
      p_minutes: minutes,
    });
    if (error || !data) {
      const message = error?.message ?? "";
      const safe = message.includes("Not enough minutes")
        ? "Not enough minutes in your Reward Bank"
        : message.includes("not approved")
        ? "That app is not approved by your parent"
        : message.includes("Choose a reward amount")
        ? message
        : "Could not redeem reward minutes. Please refresh and try again.";
      return json({ error: safe }, 400);
    }
    return json(data);
  } catch (error) {
    console.error("Reward redemption failed", error);
    return json({ error: "Could not redeem this reward" }, 500);
  }
});
