import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });
const validUuid = (value: unknown): value is string =>
  typeof value === "string" && /^[0-9a-f]{8}-[0-9a-f-]{27}$/i.test(value);

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !anonKey || !serviceKey) return json({ error: "Server configuration is incomplete" }, 500);
  const authorization = req.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) return json({ error: "Sign in as a parent first" }, 401);

  try {
    const caller = createClient(url, anonKey, {
      global: { headers: { Authorization: authorization } },
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data: authData, error: authError } = await caller.auth.getUser();
    if (authError || !authData.user) return json({ error: "Session expired; sign in again" }, 401);

    const body = await req.json();
    const childId = body.child_id;
    const countryCode = typeof body.country_code === "string" ? body.country_code.toUpperCase() : "";
    const grade = Number(body.grade_level);
    const age = Number(body.age_years);
    const subjectCode = typeof body.subject_code === "string" ? body.subject_code.toLowerCase() : "";
    const language = typeof body.language_code === "string" ? body.language_code.toLowerCase() : "en";
    if (!validUuid(childId) || !/^[A-Z]{2}$/.test(countryCode) ||
        !Number.isInteger(grade) || grade < 0 || grade > 13 ||
        !Number.isInteger(age) || age < 3 || age > 19 ||
        !/^[a-z][a-z0-9-]{1,15}$/.test(language) ||
        !/^[a-z0-9_-]{2,40}$/.test(subjectCode)) {
      return json({ error: "Choose a valid country, grade, child age, subject and language" }, 400);
    }

    const admin = createClient(url, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });
    const { data: profile, error: profileError } = await admin.schema("edulock")
      .from("user_profiles").select("account_role").eq("user_id", authData.user.id).maybeSingle();
    if (profileError || profile?.account_role !== "parent") return json({ error: "Parent access is required" }, 403);

    const { data: child, error: childError } = await admin.schema("edulock")
      .from("children").select("id,family_id").eq("id", childId).maybeSingle();
    if (childError || !child) return json({ error: "Child profile was not found" }, 404);
    const { data: family, error: familyError } = await admin.schema("edulock")
      .from("family_profiles").select("id,owner_user_id").eq("id", child.family_id).maybeSingle();
    if (familyError || family?.owner_user_id !== authData.user.id) {
      return json({ error: "You cannot view curriculum for a child outside your family" }, 403);
    }

    const selectFields = "id,country_code,curriculum_name,grade_level,age_min,age_max,subject_code,subject_name,language_code,topic,prompt,question_type,options,concept_key,source_note";
    const { data: exact, error: exactError } = await admin.schema("edulock")
      .from("curriculum_questions").select(selectFields)
      .eq("active", true).eq("country_code", countryCode).eq("grade_level", grade)
      .eq("subject_code", subjectCode).eq("language_code", language)
      .lte("age_min", age).gte("age_max", age).order("topic").limit(80);
    if (exactError) {
      console.error("Curriculum lookup failed", exactError.code, exactError.message);
      return json({ error: "Curriculum library is unavailable. Apply the latest EduLock database migrations." }, 500);
    }

    let questions = exact ?? [];
    let fallbackUsed = false;
    if (questions.length < 10 && countryCode !== "ZZ") {
      const { data: fallback, error: fallbackError } = await admin.schema("edulock")
        .from("curriculum_questions").select(selectFields)
        .eq("active", true).eq("country_code", "ZZ").eq("grade_level", grade)
        .eq("subject_code", subjectCode).eq("language_code", language)
        .lte("age_min", age).gte("age_max", age).order("topic").limit(80);
      if (fallbackError) {
        console.error("Starter curriculum lookup failed", fallbackError.code, fallbackError.message);
        return json({ error: "Could not load starter questions" }, 500);
      }
      if ((fallback ?? []).length > 0) {
        const existing = new Set(questions.map((q: any) => q.id));
        questions = [...questions, ...(fallback ?? []).filter((q: any) => !existing.has(q.id))].slice(0, 80);
        fallbackUsed = true;
      }
    }

    if (questions.length === 0 && language !== "en") {
      const { data: english, error: englishError } = await admin.schema("edulock")
        .from("curriculum_questions").select(selectFields)
        .eq("active", true).in("country_code", [countryCode, "ZZ"])
        .eq("grade_level", grade).eq("subject_code", subjectCode).eq("language_code", "en")
        .lte("age_min", age).gte("age_max", age).order("topic").limit(80);
      if (englishError) return json({ error: "Could not load English starter questions" }, 500);
      questions = english ?? [];
      fallbackUsed = questions.length > 0;
    }

    return json({
      questions: questions.map((q: any) => ({
        id: q.id, country_code: q.country_code, curriculum_name: q.curriculum_name,
        grade_level: q.grade_level, age_min: q.age_min, age_max: q.age_max,
        subject_code: q.subject_code, subject_name: q.subject_name,
        language_code: q.language_code, topic: q.topic, prompt: q.prompt,
        question_type: q.question_type, options: q.options, concept_key: q.concept_key,
        source_note: q.source_note
      })),
      fallback_used: fallbackUsed,
      requested_country: countryCode,
      requested_language: language,
      message: fallbackUsed
        ? "Some questions use the international starter bank or English because a reviewed local-language bank is not yet available. This is practice content, not a complete official syllabus."
        : "Questions are filtered by country, grade and age. Verify board-specific alignment with your child's current school syllabus."
    });
  } catch (error) {
    console.error("Curriculum library request failed", error);
    return json({ error: "Could not load curriculum questions" }, 500);
  }
});
