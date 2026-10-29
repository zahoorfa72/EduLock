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
    const { data: auth, error: authError } = await caller.auth.getUser();
    if (authError || !auth.user) return json({ error: "Session expired; sign in again" }, 401);
    const body = await req.json();
    if (typeof body.task_id !== "string" || !/^[0-9a-f-]{36}$/i.test(body.task_id)) {
      return json({ error: "A valid task is required" }, 400);
    }

    const admin = createClient(url, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });
    const { data: child, error: childError } = await admin.schema("edulock").from("children")
      .select("id,child_user_id").eq("child_user_id", auth.user.id).maybeSingle();
    if (childError || !child) return json({ error: "No linked child profile was found" }, 403);

    const { data: task, error: taskError } = await admin.schema("edulock").from("tasks")
      .select("id,child_id,subject_id,title,question_count,pass_percent,reward_minutes,daily_cap_minutes,active,reward_package_name,reward_group_id,time_limit_seconds,question_time_limit_seconds,reward_to_bank,target_grade_level,target_age_years")
      .eq("id", body.task_id).eq("child_id", child.id).eq("active", true).maybeSingle();
    if (taskError || !task) return json({ error: "Task is unavailable for this child" }, 404);

    const { data: links, error: linkError } = await admin.schema("edulock").from("task_questions")
      .select("question_id,sort_order").eq("task_id", task.id).order("sort_order", { ascending: true });
    if (linkError) return json({ error: "Could not prepare task questions" }, 500);
    let query = admin.schema("edulock").from("questions")
      .select("id,subject_id,prompt,question_type,options,language_code,concept_key,picture_path,grade_level,age_min,age_max,reward_minutes,answer_key,created_at")
      .eq("grade_level", task.target_grade_level)
      .lte("age_min", task.target_age_years).gte("age_max", task.target_age_years);
    if (links && links.length > 0) {
      query = query.in("id", links.map((link: { question_id: string }) => link.question_id));
    } else {
      query = query.eq("subject_id", task.subject_id).order("created_at", { ascending: true }).limit(task.question_count);
    }
    const { data: questions, error: questionError } = await query;
    if (questionError || !questions || questions.length === 0) return json({ error: "No questions are available for this task" }, 400);
    const ordered = links && links.length > 0
      ? links.map((link: { question_id: string }) => questions.find((q: { id: string }) => q.id === link.question_id)).filter(Boolean)
      : questions;
    const selected = ordered.slice(0, task.question_count);
    const ids = selected.map((q: { id: string }) => q.id);
    const { data: translations, error: translationError } = await admin.schema("edulock").from("question_translations")
      .select("question_id,language_code,accepted_answers").in("question_id", ids);
    if (translationError) return json({ error: "Could not prepare offline grading data" }, 500);

    return json({
      task,
      questions: selected.map((q: Record<string, unknown>) => ({
        ...q,
        accepted_answers_by_language: (translations || [])
          .filter((t: { question_id: string }) => t.question_id === q.id)
          .reduce((acc: Record<string, unknown>, t: { language_code: string; accepted_answers: unknown }) => {
            acc[t.language_code] = t.accepted_answers;
            return acc;
          }, {}),
      })),
      prepared_at: new Date().toISOString(),
    });
  } catch (error) {
    console.error("Offline task package failed", error);
    return json({ error: "Could not prepare offline task data" }, 500);
  }
});
