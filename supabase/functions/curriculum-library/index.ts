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
    // Keep age-band matching strict. If exact locale/grade content is sparse, rank useful
    // questions across the selected country and international bank, nearby grades, and English.
    const languages = [...new Set(language === "en" ? ["en"] : [language, "en"])];
    const { data: candidates, error: candidateError } = await admin.schema("edulock")
      .from("curriculum_questions").select(selectFields)
      .eq("active", true).in("country_code", [countryCode, "ZZ"])
      .gte("grade_level", 0).lte("grade_level", 13)
      .eq("subject_code", subjectCode).in("language_code", languages)
      .lte("age_min", age).gte("age_max", age)
      .order("grade_level").order("topic").limit(400);
    if (candidateError) {
      console.error("Curriculum lookup failed", candidateError.code, candidateError.message);
      return json({ error: "Curriculum library is unavailable. Apply the latest EduLock database migrations." }, 500);
    }
    const ranked = [...(candidates ?? [])].sort((a: any, b: any) => {
      const score = (q: any) =>
        Math.abs(Number(q.grade_level) - grade) * 4 +
        (q.language_code === language ? 0 : 2) +
        (q.country_code === countryCode ? 0 : 1);
      return score(a) - score(b) || String(a.topic).localeCompare(String(b.topic));
    });
    // Keep each result/import set in one language. Prefer the requested language when
    // it has comparable coverage; otherwise use English rather than mixing languages.
    const requestedLanguageQuestions = ranked.filter((q: any) => q.language_code === language);
    const englishQuestions = ranked.filter((q: any) => q.language_code === "en");
    const useRequestedLanguage = language === "en" ||
      requestedLanguageQuestions.length >= Math.min(10, englishQuestions.length);
    const chosenLanguage = useRequestedLanguage ? language : "en";
    const uniqueQuestions: any[] = [];
    const seenQuestions = new Set<string>();
    for (const q of ranked) {
      if (q.language_code !== chosenLanguage) continue;
      const key = [q.language_code, q.concept_key, q.prompt].join("|");
      if (!seenQuestions.has(key)) {
        seenQuestions.add(key);
        uniqueQuestions.push(q);
      }
    }
    const questions = uniqueQuestions.slice(0, 80);
    const fallbackUsed = chosenLanguage !== language || questions.some((q: any) =>
      Number(q.grade_level) !== grade || q.country_code !== countryCode || q.country_code === "ZZ"
    );

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
        ? "Found age-matched questions. Some use English, international starter content or a nearby grade because reviewed local material is not available for every combination. Check alignment with your child's school board."
        : "Questions match the selected country, grade, age and language. Verify board-specific alignment with your child's current school syllabus."
    });
  } catch (error) {
    console.error("Curriculum library request failed", error);
    return json({ error: "Could not load curriculum questions" }, 500);
  }
});
