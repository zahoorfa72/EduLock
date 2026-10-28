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
    if (typeof body.task_id !== "string" || !/^[0-9a-f-]{36}$/i.test(body.task_id)) {
      return json({ error: "A valid task is required" }, 400);
    }
    if (!Array.isArray(body.answers) || body.answers.length < 1 || body.answers.length > 500) {
      return json({ error: "A task must contain between 1 and 500 answers" }, 400);
    }
    for (const item of body.answers) {
      if (!item || typeof item.question_id !== "string" || typeof item.answer !== "string" || item.answer.length > 10000) {
        return json({ error: "Invalid answer format" }, 400);
      }
    }

    const admin = createClient(url, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });
    const { data: result, error: gradeError } = await admin.schema("edulock").rpc("grade_and_award_task", {
      p_user_id: authData.user.id,
      p_task_id: body.task_id,
      p_answers: body.answers,
    });
    if (gradeError || !result) {
      console.error("Server grading failed", gradeError?.code, gradeError?.message);
      const safeMessage = gradeError?.message?.includes("Please answer")
        ? "Please answer every question before submitting"
        : gradeError?.message?.includes("unavailable")
        ? "This task is no longer available"
        : gradeError?.message?.includes("questions")
        ? "This task has no questions yet"
        : "Could not submit answers. Please retry.";
      return json({ error: safeMessage }, 400);
    }
    // The SQL grader remains authoritative. This endpoint only returns its per-question
    // detail payload when the database function provides it; never infer reward values here.
    return json(result);
  } catch (error) {
    console.error("Submit attempt failed", error);
    return json({ error: "Could not submit this task" }, 500);
  }
});
