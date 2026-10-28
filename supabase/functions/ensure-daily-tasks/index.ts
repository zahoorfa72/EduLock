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
    const schema = admin.schema("edulock");
    const { data: profile, error: profileError } = await schema
      .from("user_profiles").select("account_role").eq("user_id", authData.user.id).maybeSingle();
    if (profileError || profile?.account_role !== "child") {
      return json({ error: "Only a linked child account can request its daily tasks" }, 403);
    }
    const { data: child, error: childError } = await schema
      .from("children").select("id,family_id,age_years,school_grade,preferred_language")
      .eq("child_user_id", authData.user.id).maybeSingle();
    if (childError || !child) return json({ error: "No linked child profile was found" }, 403);

    const { data: preference } = await schema.from("child_learning_preferences")
      .select("kindergarten_mode,daily_written_question_limit,practical_tasks_enabled")
      .eq("child_id", child.id).maybeSingle();
    const kindergarten = preference?.kindergarten_mode === true || Number(child.age_years ?? 8) <= 6 ||
      Number(child.school_grade ?? 1) <= 0;
    const dailyQuestionLimit = Math.max(1, Math.min(100,
      Number(preference?.daily_written_question_limit ?? (kindergarten ? 4 : 8))));
    const practicalEnabled = preference?.practical_tasks_enabled !== false;

    // The database generator remains the source of truth for idempotent daily tasks.
    const { data: generated, error: generateError } = await schema
      .rpc("ensure_daily_tasks_for_child", { p_user_id: authData.user.id });
    if (generateError || !generated) {
      console.error("Daily task generation failed", generateError?.code, generateError?.message);
      return json({ error: "Daily tasks could not be created. Check the child's subjects and saved questions." }, 400);
    }

    // Pull recent per-question history to produce a compact, explainable practice plan.
    const { data: attempts, error: attemptsError } = await schema.from("attempt_answers")
      .select("question_id,concept_key,is_correct,created_at,attempts!inner(child_id)")
      .eq("attempts.child_id", child.id)
      .order("created_at", { ascending: false }).limit(500);
    if (attemptsError) console.warn("Could not load adaptive attempt history", attemptsError.message);
    const history = attempts ?? [];
    const conceptStats = new Map<string, { wrong: number; correct: number; lastWrong: string | null }>();
    const seenQuestions = new Map<string, { wrong: number; correct: number; lastAt: string }>();
    for (const answer of history) {
      const key = String(answer.concept_key ?? "general");
      const stat = conceptStats.get(key) ?? { wrong: 0, correct: 0, lastWrong: null };
      if (answer.is_correct) stat.correct++; else { stat.wrong++; stat.lastWrong ??= String(answer.created_at ?? ""); }
      conceptStats.set(key, stat);
      const qid = String(answer.question_id ?? "");
      if (qid) {
        const qstat = seenQuestions.get(qid) ?? { wrong: 0, correct: 0, lastAt: String(answer.created_at ?? "") };
        if (answer.is_correct) qstat.correct++; else qstat.wrong++;
        seenQuestions.set(qid, qstat);
      }
    }
    const focusConcepts = [...conceptStats.entries()]
      .map(([concept_key, stat]) => ({ concept_key, ...stat, focus_score: Math.max(0, stat.wrong * 2 - stat.correct) }))
      .filter((item) => item.focus_score > 0)
      .sort((a, b) => b.focus_score - a.focus_score)
      .slice(0, 8);

    // Store progress summary for parent/child reports; RLS blocks clients from forging it.
    for (const item of focusConcepts) {
      const stats = conceptStats.get(item.concept_key)!;
      // The database grader is authoritative for concept_progress writes. This API
      // only derives an in-memory focus summary from this child's own attempt history.
    }

    // Distinct practical portal feed. It is deliberately not attached to written tasks.
    let practicalTasks: unknown[] = [];
    if (practicalEnabled) {
      const age = Number(child.age_years ?? 8);
      const { data, error } = await schema.from("practical_tasks")
        .select("id,title,instructions,materials,safety_notes,age_min,age_max,activity_kind,expected_minutes")
        .eq("child_id", child.id).eq("active", true)
        .lte("age_min", age).gte("age_max", age)
        .order("created_at", { ascending: false }).limit(10);
      if (error) console.warn("Practical task feed unavailable", error.message);
      practicalTasks = data ?? [];
    }
    return json({
      ...((typeof generated === "object" && generated !== null) ? generated : { generated }),
      adaptive_learning: {
        kindergarten_mode: kindergarten,
        daily_question_limit: dailyQuestionLimit,
        focus_concepts: focusConcepts,
        repeat_corrected_questions: true,
        exclude_practical_from_written_tasks: true,
      },
      practical_tasks: practicalTasks,
    });
  } catch (error) {
    console.error("Ensure daily tasks failed", error);
    return json({ error: "Daily tasks could not be generated" }, 500);
  }
});
