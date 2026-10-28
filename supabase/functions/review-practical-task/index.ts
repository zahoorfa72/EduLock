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
        typeof body.status !== "string" || !["approved","needs_practice"].includes(body.status) ||
        (body.parent_feedback != null && typeof body.parent_feedback !== "string")) {
      return json({ error: "Invalid practical-task review request" }, 400);
    }
    const feedback = String(body.parent_feedback ?? "").trim();
    if (feedback.length > 1000) return json({ error: "Feedback must be 1,000 characters or fewer" }, 400);

    const admin = createClient(url, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });
    const schema = admin.schema("edulock");
    const { data: profile, error: profileError } = await schema
      .from("user_profiles").select("account_role").eq("user_id", authData.user.id).maybeSingle();
    if (profileError || profile?.account_role !== "parent") {
      return json({ error: "Only a parent can review practical task submissions" }, 403);
    }
    const { data: submission, error: submissionError } = await schema
      .from("practical_task_submissions")
      .select("id,child_id,status,practical_tasks!inner(id,family_id,child_id)")
      .eq("id", body.submission_id).maybeSingle();
    if (submissionError || !submission) return json({ error: "Submission not found" }, 404);
    if (submission.status !== "submitted") return json({ error: "This submission has already been reviewed" }, 409);

    const joined = Array.isArray(submission.practical_tasks)
      ? submission.practical_tasks[0] : submission.practical_tasks;
    if (!joined || joined.child_id !== submission.child_id) {
      return json({ error: "Practical task ownership check failed" }, 403);
    }
    const { data: child, error: childError } = await schema
      .from("children").select("id,family_id").eq("id", submission.child_id).maybeSingle();
    if (childError || !child || child.family_id !== joined.family_id) {
      return json({ error: "Child/family ownership check failed" }, 403);
    }
    const { data: family, error: familyError } = await schema
      .from("family_profiles").select("id").eq("id", child.family_id).eq("parent_user_id", authData.user.id).maybeSingle();
    if (familyError || !family) return json({ error: "You cannot review another family's submission" }, 403);

    const { data: reviewed, error: updateError } = await schema
      .from("practical_task_submissions")
      .update({
        status: body.status,
        parent_feedback: feedback,
        reviewed_at: new Date().toISOString(),
        reviewed_by: authData.user.id,
      })
      .eq("id", body.submission_id).eq("status", "submitted")
      .select("id,status,parent_feedback,reviewed_at").maybeSingle();
    if (updateError || !reviewed) {
      console.error("Practical task review failed", updateError?.code, updateError?.message);
      return json({ error: "Could not save this practical-task review" }, 400);
    }
    return json({ submission: reviewed });
  } catch (error) {
    console.error("Practical task review failed", error);
    return json({ error: "Could not review this practical task" }, 500);
  }
});
