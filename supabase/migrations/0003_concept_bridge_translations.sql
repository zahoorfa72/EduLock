-- Concept Bridge: translate the presentation of one learning question while preserving its
-- canonical identity, and report concept understanding independently by language.
grant usage on schema edulock to service_role;

-- Edge Functions use the service-role key only on the server; direct table access is narrowly granted.
grant select, insert, update on edulock.user_profiles, edulock.children, edulock.family_profiles,
  edulock.child_pairing_codes, edulock.pairing_claim_limits to service_role;
grant select, insert, update, delete on edulock.questions, edulock.question_translations,
  edulock.attempts, edulock.attempt_answers, edulock.screen_time_ledger to service_role;
grant select, insert, update, delete on edulock.tasks, edulock.subjects, edulock.allowed_apps to service_role;

alter table edulock.questions
  add column if not exists language_code text not null default 'en',
  add column if not exists concept_key text not null default '';

alter table edulock.questions
  drop constraint if exists questions_language_code_length;
alter table edulock.questions
  add constraint questions_language_code_length check (char_length(language_code) between 2 and 16);
alter table edulock.questions
  drop constraint if exists questions_concept_key_length;
alter table edulock.questions
  add constraint questions_concept_key_length check (char_length(concept_key) between 1 and 160);

create table if not exists edulock.question_translations (
  id uuid primary key default gen_random_uuid(),
  question_id uuid not null references edulock.questions(id) on delete cascade,
  language_code text not null check (char_length(language_code) between 2 and 16),
  prompt text not null check (char_length(prompt) between 1 and 10000),
  options jsonb not null default '[]'::jsonb check (jsonb_typeof(options) = 'array'),
  accepted_answers jsonb not null default '[]'::jsonb check (
    jsonb_typeof(accepted_answers) = 'array' and jsonb_array_length(accepted_answers) between 1 and 30
  ),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(question_id, language_code)
);
create index if not exists question_translations_question_language_idx
  on edulock.question_translations(question_id, language_code);
alter table edulock.question_translations enable row level security;
revoke all on edulock.question_translations from public, anon, authenticated;
grant select (id, question_id, language_code, prompt, options, created_at, updated_at)
  on edulock.question_translations to authenticated;
grant insert, update, delete on edulock.question_translations to authenticated;
grant select, insert, update, delete on edulock.question_translations to service_role;

drop policy if exists question_translations_parent_all on edulock.question_translations;
create policy question_translations_parent_all on edulock.question_translations
  for all to authenticated
  using (
    edulock.is_parent() and exists (
      select 1 from edulock.questions q
      where q.id = question_translations.question_id and edulock.owns_subject(q.subject_id)
    )
  )
  with check (
    edulock.is_parent() and exists (
      select 1 from edulock.questions q
      where q.id = question_translations.question_id and edulock.owns_subject(q.subject_id)
    )
  );
drop policy if exists question_translations_child_assigned_select on edulock.question_translations;
create policy question_translations_child_assigned_select on edulock.question_translations
  for select to authenticated
  using (
    exists (
      select 1
      from edulock.questions q
      join edulock.tasks t on t.subject_id = q.subject_id and t.active = true
      join edulock.children c on c.id = t.child_id
      where q.id = question_translations.question_id and c.child_user_id = (select auth.uid())
    )
  );

-- Individual results preserve the concept tag and the language actually used for each answer.
create table if not exists edulock.attempt_answers (
  id uuid primary key default gen_random_uuid(),
  attempt_id uuid not null references edulock.attempts(id) on delete cascade,
  question_id uuid not null references edulock.questions(id) on delete cascade,
  concept_key text not null check (char_length(concept_key) between 1 and 160),
  language_code text not null check (char_length(language_code) between 2 and 16),
  is_correct boolean not null,
  created_at timestamptz not null default now(),
  unique(attempt_id, question_id)
);
create index if not exists attempt_answers_concept_language_idx
  on edulock.attempt_answers(concept_key, language_code, created_at desc);
alter table edulock.attempt_answers enable row level security;
revoke all on edulock.attempt_answers from public, anon, authenticated;
grant select (id, attempt_id, question_id, concept_key, language_code, is_correct, created_at)
  on edulock.attempt_answers to authenticated;
grant select, insert, update, delete on edulock.attempt_answers to service_role;

drop policy if exists attempt_answers_parent_read on edulock.attempt_answers;
create policy attempt_answers_parent_read on edulock.attempt_answers
  for select to authenticated using (
    exists (
      select 1 from edulock.attempts a
      where a.id = attempt_answers.attempt_id and edulock.owns_child(a.child_id)
    )
  );
drop policy if exists attempt_answers_child_read on edulock.attempt_answers;
create policy attempt_answers_child_read on edulock.attempt_answers
  for select to authenticated using (
    exists (
      select 1 from edulock.attempts a
      join edulock.children c on c.id = a.child_id
      where a.id = attempt_answers.attempt_id and c.child_user_id = (select auth.uid())
    )
  );

-- Preserve every original question column except answer_key, and expose the bridge metadata.
revoke select on edulock.questions from authenticated;
grant select (id, subject_id, prompt, question_type, options, image_path, audio_path,
  language_code, concept_key, created_at) on edulock.questions to authenticated;

-- Re-grade on the server, using accepted answers from the language selected for each question.
-- The client never receives accepted_answers or answer_key.
create or replace function edulock.grade_and_award_task(
  p_user_id uuid, p_task_id uuid, p_answers jsonb
) returns jsonb
language plpgsql security definer set search_path = ''
as $$
declare
  v_child edulock.children%rowtype;
  v_task edulock.tasks%rowtype;
  v_question edulock.questions%rowtype;
  v_translation edulock.question_translations%rowtype;
  v_answer jsonb;
  v_matches integer;
  v_total integer := 0;
  v_correct integer := 0;
  v_score integer := 0;
  v_passed boolean := false;
  v_daily_used integer := 0;
  v_task_used integer := 0;
  v_award integer := 0;
  v_attempt_id uuid;
  v_today timestamptz;
  v_expected text;
  v_actual text;
  v_lang text;
  v_accept jsonb;
  v_is_correct boolean;
  v_details jsonb := '[]'::jsonb;
  v_candidate jsonb;
begin
  if jsonb_typeof(p_answers) <> 'array' then raise exception 'Answers must be an array'; end if;
  if jsonb_array_length(p_answers) > 500 then raise exception 'Too many answers'; end if;

  select c.* into v_child from edulock.children c where c.child_user_id = p_user_id;
  if not found then raise exception 'No linked child profile was found'; end if;
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_child.id::text, 0));

  select t.* into v_task from edulock.tasks t
    where t.id = p_task_id and t.child_id = v_child.id and t.active = true;
  if not found then raise exception 'Task is unavailable for this child'; end if;
  if v_task.subject_id is null then raise exception 'This task has no subject'; end if;

  select count(*) into v_total from (
    select q.id from edulock.questions q where q.subject_id = v_task.subject_id
    order by q.created_at, q.id limit v_task.question_count
  ) selected_questions;
  if v_total < 1 then raise exception 'This task has no questions yet'; end if;
  if jsonb_array_length(p_answers) <> v_total then
    raise exception 'Please answer every question before submitting';
  end if;

  for v_question in
    select q.* from edulock.questions q where q.subject_id = v_task.subject_id
    order by q.created_at, q.id limit v_task.question_count
  loop
    select count(*) into v_matches from jsonb_array_elements(p_answers) item
      where item ->> 'question_id' = v_question.id::text;
    if v_matches <> 1 then raise exception 'Each question must have exactly one answer'; end if;
    select item into v_answer from jsonb_array_elements(p_answers) item
      where item ->> 'question_id' = v_question.id::text limit 1;

    v_lang := coalesce(nullif(v_answer ->> 'language_code', ''), v_question.language_code);
    if char_length(v_lang) > 16 then raise exception 'Invalid language code'; end if;
    v_actual := coalesce(v_answer ->> 'answer', '');
    if char_length(v_actual) > 10000 then raise exception 'Answer is too long'; end if;
    v_accept := null;
    select qt.accepted_answers into v_accept
      from edulock.question_translations qt
      where qt.question_id = v_question.id and qt.language_code = v_lang;

    v_is_correct := false;
    if v_accept is not null and jsonb_typeof(v_accept) = 'array' then
      for v_candidate in select value from jsonb_array_elements(v_accept)
      loop
        v_expected := coalesce(v_candidate #>> '{}', '');
        if v_expected <> '' and
          pg_catalog.regexp_replace(pg_catalog.lower(pg_catalog.btrim(v_expected)), '\s+', ' ', 'g') =
          pg_catalog.regexp_replace(pg_catalog.lower(pg_catalog.btrim(v_actual)), '\s+', ' ', 'g') then
          v_is_correct := true;
          exit;
        end if;
      end loop;
    else
      v_expected := coalesce(v_question.answer_key ->> 'answer', '');
      v_is_correct := v_expected <> '' and
        pg_catalog.regexp_replace(pg_catalog.lower(pg_catalog.btrim(v_expected)), '\s+', ' ', 'g') =
        pg_catalog.regexp_replace(pg_catalog.lower(pg_catalog.btrim(v_actual)), '\s+', ' ', 'g');
    end if;

    if v_is_correct then v_correct := v_correct + 1; end if;
    v_details := v_details || jsonb_build_array(jsonb_build_object(
      'question_id', v_question.id,
      'concept_key', coalesce(nullif(v_question.concept_key, ''), 'general'),
      'language_code', v_lang,
      'is_correct', v_is_correct
    ));
  end loop;

  v_score := floor((v_correct::numeric * 100) / v_total)::integer;
  v_passed := v_score >= v_task.pass_percent;
  v_today := pg_catalog.date_trunc('day', now() at time zone 'UTC') at time zone 'UTC';

  select coalesce(sum(l.minutes_delta), 0)::integer into v_daily_used
    from edulock.screen_time_ledger l where l.child_id = v_child.id and l.created_at >= v_today;
  select coalesce(sum(l.minutes_delta), 0)::integer into v_task_used
    from edulock.screen_time_ledger l
    join edulock.attempts a on a.id = l.source_attempt_id
    where l.child_id = v_child.id and a.task_id = v_task.id and l.created_at >= v_today;

  if v_passed then
    v_award := greatest(0, least(
      v_task.reward_minutes,
      v_child.daily_cap_minutes - v_daily_used,
      v_task.daily_cap_minutes - v_task_used
    ));
  end if;

  insert into edulock.attempts(task_id, child_id, answers, score_percent, passed, awarded_minutes)
    values (v_task.id, v_child.id, p_answers, v_score, v_passed, v_award)
    returning id into v_attempt_id;

  insert into edulock.attempt_answers(attempt_id, question_id, concept_key, language_code, is_correct)
    select v_attempt_id, x.question_id, x.concept_key, x.language_code, x.is_correct
    from jsonb_to_recordset(v_details) as x(
      question_id uuid, concept_key text, language_code text, is_correct boolean
    );

  if v_award > 0 then
    insert into edulock.screen_time_ledger(child_id, source_attempt_id, minutes_delta, reason)
      values (v_child.id, v_attempt_id, v_award, 'Task reward: ' || v_task.title);
  end if;

  return jsonb_build_object(
    'attempt_id', v_attempt_id, 'score_percent', v_score, 'passed', v_passed,
    'awarded_minutes', v_award, 'daily_used_minutes_after', v_daily_used + v_award,
    'daily_cap_minutes', v_child.daily_cap_minutes
  );
end;
$$;
revoke all on function edulock.grade_and_award_task(uuid, uuid, jsonb) from public, anon, authenticated;
grant execute on function edulock.grade_and_award_task(uuid, uuid, jsonb) to service_role;
