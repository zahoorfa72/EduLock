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
  if (!authorization?.startsWith("Bearer ")) return json({ error: "Sign in first" }, 401);

  try {
    const caller = createClient(url, anonKey, {
      global: { headers: { Authorization: authorization } },
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data: authData, error: authError } = await caller.auth.getUser();
    if (authError || !authData.user) return json({ error: "Session expired; sign in again" }, 401);
    const body = await req.json();
    if (typeof body.submission_id !== "string" || !/^[0-9a-f-]{36}$/i.test(body.submission_id) ||
        typeof body.approve !== "boolean" || (body.review_note != null && typeof body.review_note !== "string")) {
      return json({ error: "Invalid review request" }, 400);
    }
    const admin = createClient(url, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });
    const { data: result, error } = await admin.schema("edulock").rpc("review_physical_challenge_submission", {
      p_user_id: authData.user.id,
      p_submission_id: body.submission_id,
      p_approved: body.approve,
      p_review_note: String(body.review_note ?? "").slice(0, 500),
    });
    if (error || !result) {
      console.error("Challenge review failed", error?.code, error?.message);
      return json({ error: error?.message?.includes("cannot review") ? "You cannot review another family's submission." : "Could not review this submission." }, 400);
    }
    return json(result);
  } catch (error) {
    console.error("Challenge review failed", error);
    return json({ error: "Could not review this submission." }, 500);
  }
});
