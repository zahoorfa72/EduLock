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
    const rawIds = Array.isArray(body.question_ids) ? body.question_ids : [];
    const ids = [...new Set(rawIds.filter(validUuid))] as string[];
    if (!validUuid(childId) || !/^[A-Z]{2}$/.test(countryCode) ||
        !Number.isInteger(grade) || grade < 0 || grade > 13 ||
        !Number.isInteger(age) || age < 3 || age > 19 ||
        !/^[a-z][a-z0-9-]{1,15}$/.test(language) ||
        !/^[a-z0-9_-]{2,40}$/.test(subjectCode) ||
        ids.length < 1 || ids.length > 50 || ids.length !== rawIds.length) {
      return json({ error: "Choose 1–50 valid age- and grade-matched questions" }, 400);
    }

    const admin = createClient(url, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });
    const { data: profile, error: profileError } = await admin.schema("edulock")
      .from("user_profiles").select("account_role").eq("user_id", authData.user.id).maybeSingle();
    if (profileError || profile?.account_role !== "parent") return json({ error: "Parent access is required" }, 403);

    const { data: child, error: childError } = await admin.schema("edulock")
      .from("children").select("id,family_id,daily_cap_minutes").eq("id", childId).maybeSingle();
    if (childError || !child) return json({ error: "Child profile was not found" }, 404);
    const { data: family, error: familyError } = await admin.schema("edulock")
      .from("family_profiles").select("id,owner_user_id").eq("id", child.family_id).maybeSingle();
    if (familyError || family?.owner_user_id !== authData.user.id) return json({ error: "This child is not in your family" }, 403);

    const { data: bankQuestions, error: bankError } = await admin.schema("edulock")
      .from("curriculum_questions").select("*").in("id", ids).eq("active", true)
      .in("country_code", [countryCode, "ZZ"])
      .eq("grade_level", grade)
      .eq("subject_code", subjectCode).in("language_code", [...new Set(language === "en" ? ["en"] : [language, "en"])])
      .lte("age_min", age).gte("age_max", age);
    if (bankError) {
      console.error("Curriculum import lookup failed", bankError.code, bankError.message);
      return json({ error: "Curriculum bank is unavailable. Apply the latest EduLock migrations." }, 500);
    }
    const byId = new Map((bankQuestions ?? []).map((q: any) => [q.id, q]));
    if (ids.some((id) => !byId.has(id))) return json({ error: "One or more selected questions do not match this country, grade, age or subject. Refresh and try again." }, 400);
    const questions = ids.map((id) => byId.get(id)!);
    const actualLanguage = questions[0].language_code;
    if (questions.some((q: any) => q.language_code !== actualLanguage)) return json({ error: "Select questions from one language at a time." }, 400);

    const first = questions[0];
    const title = String(first.subject_name) + " — Grade " + String(grade);
    const { data: existingSubject, error: subjectLookupError } = await admin.schema("edulock")
      .from("subjects").select("id,title,language_code")
      .eq("family_id", child.family_id).eq("title", title)
      .eq("language_code", actualLanguage).eq("country_code", countryCode)
      .eq("grade_level", grade).eq("curriculum_subject_code", subjectCode).maybeSingle();
    if (subjectLookupError) {
      console.error("Subject lookup failed", subjectLookupError.code, subjectLookupError.message);
      return json({ error: "Could not load the target subject. Apply the latest EduLock migration." }, 500);
    }

    let subject = existingSubject;
    if (!subject) {
      const { data: createdSubject, error: subjectInsertError } = await admin.schema("edulock").from("subjects")
        .insert({
          family_id: child.family_id,
          title,
          language_code: actualLanguage,
          country_code: countryCode,
          grade_level: grade,
          curriculum_name: first.country_code === "ZZ" ? "International foundational starter bank" : first.curriculum_name,
          curriculum_subject_code: subjectCode,
        }).select("id,title,language_code").single();
      if (subjectInsertError || !createdSubject) {
        console.error("Subject create failed", subjectInsertError?.code, subjectInsertError?.message);
        return json({ error: "Could not create the curriculum subject" }, 500);
      }
      subject = createdSubject;
    }

    const rows = questions.map((q: any) => ({
      subject_id: subject!.id,
      curriculum_question_id: q.id,
      prompt: q.prompt,
      question_type: q.question_type,
      options: q.options ?? [],
      answer_key: q.answer_key ?? {},
      language_code: q.language_code,
      concept_key: q.concept_key || q.topic || "general",
      grade_level: q.grade_level,
      age_min: q.age_min,
      age_max: q.age_max,
    }));
    const { data: imported, error: importError } = await admin.schema("edulock").from("questions")
      .upsert(rows, { onConflict: "subject_id,curriculum_question_id", ignoreDuplicates: true })
      .select("id");
    if (importError) {
      console.error("Curriculum import failed", importError.code, importError.message);
      return json({ error: "Could not import these curriculum questions" }, 500);
    }

    // Only link questions that match both the selected class and this child's exact age.
    const { data: eligibleQuestions, error: countError } = await admin.schema("edulock")
      .from("questions").select("id")
      .eq("subject_id", subject.id).eq("grade_level", grade)
      .lte("age_min", age).gte("age_max", age)
      .order("created_at").limit(500);
    if (countError) {
      console.error("Curriculum question count failed", countError.code, countError.message);
      return json({ error: "Questions were imported, but the matching task could not be prepared. Open Assign Task to create it." }, 500);
    }
    if (!eligibleQuestions?.length) {
      return json({ error: "The selected questions do not match this child's grade and age. Choose matching questions and try again." }, 400);
    }
    const questionCount = eligibleQuestions.length;
    const taskTitle = "Curriculum practice — " + String(first.subject_name) + " — Grade " + String(grade);
    const dailyCap = Math.max(0, Math.min(Number(child.daily_cap_minutes ?? 60), 1440));
    const { data: existingTask, error: taskLookupError } = await admin.schema("edulock")
      .from("tasks").select("id").eq("family_id", child.family_id).eq("child_id", childId)
      .eq("subject_id", subject.id).eq("title", taskTitle).eq("active", true).limit(1).maybeSingle();
    if (taskLookupError) {
      console.error("Curriculum task lookup failed", taskLookupError.code, taskLookupError.message);
      return json({ error: "Questions were imported, but the learning task could not be loaded. Open Assign Task to create it." }, 500);
    }

    let taskId = existingTask?.id as string | undefined;
    if (taskId) {
      const { error: taskUpdateError } = await admin.schema("edulock").from("tasks").update({
        target_grade_level: grade,
        target_age_years: age,
        question_count: questionCount,
        pass_percent: 70,
        reward_minutes: Math.min(10, dailyCap),
        daily_cap_minutes: dailyCap,
        active: true,
      }).eq("id", taskId);
      if (taskUpdateError) {
        console.error("Curriculum task update failed", taskUpdateError.code, taskUpdateError.message);
        return json({ error: "Questions were imported, but the learning task could not be updated. Open Assign Task to create it." }, 500);
      }
    } else {
      const { data: createdTask, error: taskInsertError } = await admin.schema("edulock").from("tasks")
        .insert({
          family_id: child.family_id,
          child_id: childId,
          subject_id: subject.id,
          target_grade_level: grade,
          target_age_years: age,
          title: taskTitle,
          question_count: questionCount,
          pass_percent: 70,
          reward_minutes: Math.min(10, dailyCap),
          daily_cap_minutes: dailyCap,
          active: true,
        }).select("id").single();
      if (taskInsertError || !createdTask) {
        console.error("Curriculum task create failed", taskInsertError?.code, taskInsertError?.message);
        return json({ error: "Questions were imported, but the learning task could not be created. Open Assign Task to create it." }, 500);
      }
      taskId = createdTask.id;
    }

    // Replace links atomically enough for this admin workflow so this task
    // contains only the selected grade/age questions, even if the subject has mixed imports.
    const { error: clearLinksError } = await admin.schema("edulock").from("task_questions")
      .delete().eq("task_id", taskId);
    if (clearLinksError) {
      console.error("Old task question links could not be cleared", clearLinksError.code, clearLinksError.message);
      return json({ error: "Questions were imported, but the age-matched task could not be refreshed." }, 500);
    }
    const links = eligibleQuestions.map((q: any, index: number) => ({
      task_id: taskId,
      question_id: q.id,
      sort_order: index,
    }));
    const { error: linkError } = await admin.schema("edulock").from("task_questions").insert(links);
    if (linkError) {
      console.error("Age-matched task question links could not be saved", linkError.code, linkError.message);
      return json({ error: "Questions were imported, but the age-matched task could not be prepared." }, 500);
    }

    return json({
      subject_id: subject.id,
      subject_title: subject.title,
      task_id: taskId,
      task_title: taskTitle,
      language_code: subject.language_code,
      imported_count: imported?.length ?? 0,
      selected_count: questions.length,
      question_count: questionCount,
      message: "Questions were added to the subject and an active learning task was assigned to the child. The child device will see it after syncing."
    });
  } catch (error) {
    console.error("Curriculum import failed", error);
    return json({ error: "Could not import curriculum questions" }, 500);
  }
});
