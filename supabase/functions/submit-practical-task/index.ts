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

    const body = await req.json();
    if (typeof body.practical_task_id !== "string" || !/^[0-9a-f-]{36}$/i.test(body.practical_task_id)) {
      return json({ error: "A valid practical task is required" }, 400);
    }
    if (body.completion_note != null && (typeof body.completion_note !== "string" || body.completion_note.length > 2000)) {
      return json({ error: "Completion note must be 2,000 characters or fewer" }, 400);
    }
    if (body.evidence_path != null && (typeof body.evidence_path !== "string" || body.evidence_path.length > 500)) {
      return json({ error: "Invalid evidence path" }, 400);
    }

    const admin = createClient(url, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });
    const schema = admin.schema("edulock");
    const { data: profile, error: profileError } = await schema
      .from("user_profiles").select("account_role").eq("user_id", authData.user.id).maybeSingle();
    if (profileError || profile?.account_role !== "child") {
      return json({ error: "Only the linked child account can submit a practical task" }, 403);
    }
    const { data: child, error: childError } = await schema
      .from("children").select("id").eq("child_user_id", authData.user.id).maybeSingle();
    if (childError || !child) return json({ error: "No linked child profile was found" }, 403);
    const { data: task, error: taskError } = await schema
      .from("practical_tasks").select("id,child_id,active").eq("id", body.practical_task_id)
      .eq("child_id", child.id).eq("active", true).maybeSingle();
    if (taskError || !task) return json({ error: "This practical task is not available to this child" }, 404);

    const { data: submission, error: insertError } = await schema
      .from("practical_task_submissions")
      .insert({
        practical_task_id: task.id,
        child_id: child.id,
        completion_note: String(body.completion_note ?? "").trim(),
        evidence_path: body.evidence_path ?? null,
        status: "submitted",
      })
      .select("id,status,submitted_at").single();
    if (insertError || !submission) {
      console.error("Practical task submission failed", insertError?.code, insertError?.message);
      return json({ error: "Could not submit this practical task. Please retry." }, 400);
    }
    return json({ submission });
  } catch (error) {
    console.error("Practical task submit failed", error);
    return json({ error: "Could not submit this practical task" }, 500);
  }
});
