-- Follow-up migration: keep adaptive generation compatible with existing daily-task settings.
begin;

alter table edulock.questions
  add column if not exists learning_mode text not null default 'written',
  add column if not exists difficulty_level integer not null default 1,
  add column if not exists accepted_keywords jsonb not null default '[]'::jsonb,
  add column if not exists feedback_hint text not null default '';
alter table edulock.tasks
  add column if not exists task_portal text not null default 'written',
  add column if not exists kindergarten_mode boolean not null default false,
  add column if not exists auto_task_date date,
  add column if not exists auto_task_number integer;
create unique index if not exists tasks_one_auto_task_per_day
  on edulock.tasks(child_id, auto_task_date, auto_task_number)
  where auto_task_date is not null and auto_task_number is not null;

create table if not exists edulock.child_learning_preferences (
  child_id uuid primary key references edulock.children(id) on delete cascade,
  kindergarten_mode boolean not null default false,
  gentle_reminders boolean not null default true,
  daily_written_question_limit integer not null default 8 check (daily_written_question_limit between 1 and 100),
  practical_tasks_enabled boolean not null default true,
  updated_at timestamptz not null default now()
);
alter table edulock.child_learning_preferences enable row level security;
drop policy if exists learning_preferences_parent_all on edulock.child_learning_preferences;
create policy learning_preferences_parent_all on edulock.child_learning_preferences
  for all to authenticated using (edulock.owns_child(child_id) and edulock.is_parent())
  with check (edulock.owns_child(child_id) and edulock.is_parent());
drop policy if exists learning_preferences_child_read on edulock.child_learning_preferences;
create policy learning_preferences_child_read on edulock.child_learning_preferences
  for select to authenticated using (
    exists (select 1 from edulock.children c
      where c.id = child_learning_preferences.child_id and c.child_user_id = (select auth.uid()))
  );
grant select, insert, update, delete on edulock.child_learning_preferences to authenticated;

create table if not exists edulock.child_concept_progress (
  child_id uuid not null references edulock.children(id) on delete cascade,
  concept_key text not null check (char_length(concept_key) between 1 and 160),
  subject_id uuid references edulock.subjects(id) on delete set null,
  attempts integer not null default 0 check (attempts >= 0),
  correct_attempts integer not null default 0 check (correct_attempts between 0 and attempts),
  wrong_attempts integer not null default 0 check (wrong_attempts >= 0),
  focus_score numeric(6,2) not null default 0 check (focus_score >= 0),
  last_attempt_at timestamptz,
  next_review_at timestamptz,
  last_question_id uuid references edulock.questions(id) on delete set null,
  updated_at timestamptz not null default now(),
  primary key(child_id, concept_key)
);
alter table edulock.child_concept_progress
  add column if not exists subject_id uuid references edulock.subjects(id) on delete set null;
update edulock.child_concept_progress cp
set subject_id = q.subject_id
from edulock.questions q
where cp.subject_id is null and cp.last_question_id = q.id;
update edulock.child_concept_progress cp
set subject_id = history.subject_id
from (
  select distinct on (a.child_id, aa.concept_key) a.child_id, aa.concept_key, q.subject_id
  from edulock.attempt_answers aa
  join edulock.attempts a on a.id = aa.attempt_id
  join edulock.questions q on q.id = aa.question_id
  order by a.child_id, aa.concept_key, aa.created_at desc
) history
where cp.subject_id is null and cp.child_id = history.child_id and cp.concept_key = history.concept_key;
delete from edulock.child_concept_progress where subject_id is null;
alter table edulock.child_concept_progress alter column subject_id set not null;
alter table edulock.child_concept_progress drop constraint if exists child_concept_progress_pkey;
alter table edulock.child_concept_progress add constraint child_concept_progress_pkey
  primary key(child_id, concept_key, subject_id);
create index if not exists child_concept_review_idx
  on edulock.child_concept_progress(child_id, focus_score desc, next_review_at);
alter table edulock.child_concept_progress enable row level security;
drop policy if exists concept_progress_parent_read on edulock.child_concept_progress;
create policy concept_progress_parent_read on edulock.child_concept_progress
  for select to authenticated using (edulock.owns_child(child_id));
drop policy if exists concept_progress_child_read on edulock.child_concept_progress;
create policy concept_progress_child_read on edulock.child_concept_progress
  for select to authenticated using (
    exists (select 1 from edulock.children c
      where c.id = child_concept_progress.child_id and c.child_user_id = (select auth.uid()))
  );
revoke insert, update, delete on edulock.child_concept_progress from public, anon, authenticated;
grant select on edulock.child_concept_progress to authenticated;

create table if not exists edulock.attempt_answer_feedback (
  id uuid primary key default gen_random_uuid(),
  attempt_id uuid not null references edulock.attempts(id) on delete cascade,
  question_id uuid not null references edulock.questions(id) on delete cascade,
  child_id uuid not null references edulock.children(id) on delete cascade,
  concept_key text not null default 'general',
  supplied_answer text not null default '' check (char_length(supplied_answer) <= 10000),
  expected_answer text not null default '' check (char_length(expected_answer) <= 10000),
  accuracy_score integer not null default 0 check (accuracy_score between 0 and 100),
  spelling_score integer not null default 0 check (spelling_score between 0 and 100),
  final_score integer not null default 0 check (final_score between 0 and 100),
  detected_errors jsonb not null default '[]'::jsonb,
  feedback text not null default '',
  created_at timestamptz not null default now(),
  unique(attempt_id, question_id)
);
create index if not exists answer_feedback_child_recent_idx on edulock.attempt_answer_feedback(child_id, created_at desc);
alter table edulock.attempt_answer_feedback enable row level security;
drop policy if exists answer_feedback_parent_read on edulock.attempt_answer_feedback;
create policy answer_feedback_parent_read on edulock.attempt_answer_feedback for select to authenticated using (edulock.owns_child(child_id));
drop policy if exists answer_feedback_child_read on edulock.attempt_answer_feedback;
create policy answer_feedback_child_read on edulock.attempt_answer_feedback for select to authenticated using (
  exists (select 1 from edulock.children c where c.id = attempt_answer_feedback.child_id and c.child_user_id = (select auth.uid()))
);
revoke insert, update, delete on edulock.attempt_answer_feedback from public, anon, authenticated;
grant select on edulock.attempt_answer_feedback to authenticated;

create or replace function edulock.learning_text_edit_distance(p_left text, p_right text)
returns integer language plpgsql immutable strict set search_path = ''
as $distance$
declare
  v_left text := pg_catalog.lower(p_left);
  v_right text := pg_catalog.lower(p_right);
  v_left_len integer := pg_catalog.length(p_left);
  v_right_len integer := pg_catalog.length(p_right);
  v_previous integer[];
  v_current integer[];
  v_i integer;
  v_j integer;
  v_cost integer;
begin
  if v_left_len > 500 or v_right_len > 500 then return greatest(v_left_len, v_right_len); end if;
  v_previous := pg_catalog.array_fill(0, array[v_right_len + 1]);
  for v_j in 0..v_right_len loop v_previous[v_j + 1] := v_j; end loop;
  for v_i in 1..v_left_len loop
    v_current := pg_catalog.array_fill(0, array[v_right_len + 1]);
    v_current[1] := v_i;
    for v_j in 1..v_right_len loop
      v_cost := case when pg_catalog.substr(v_left, v_i, 1) = pg_catalog.substr(v_right, v_j, 1) then 0 else 1 end;
      v_current[v_j + 1] := least(v_current[v_j] + 1, v_previous[v_j + 1] + 1, v_previous[v_j] + v_cost);
    end loop;
    v_previous := v_current;
  end loop;
  return v_previous[v_right_len + 1];
end;
$distance$;
revoke all on function edulock.learning_text_edit_distance(text,text) from public, anon, authenticated;
grant execute on function edulock.learning_text_edit_distance(text,text) to service_role;

create or replace function edulock.ensure_daily_tasks_for_child(p_user_id uuid)
returns jsonb language plpgsql security definer set search_path = ''
as $dailyfix$
declare
  v_child edulock.children%rowtype;
  v_config edulock.child_daily_task_settings%rowtype;
  v_preferences edulock.child_learning_preferences%rowtype;
  v_subject_ids uuid[];
  v_already_today uuid[];
  v_review_ids uuid[];
  v_fresh_ids uuid[];
  v_fill_ids uuid[];
  v_question_ids uuid[];
  v_question_id uuid;
  v_first_subject_id uuid;
  v_task_id uuid;
  v_today date := (now() at time zone 'UTC')::date;
  v_day_start timestamptz := date_trunc('day', now() at time zone 'UTC') at time zone 'UTC';
  v_task_number integer;
  v_link_order integer;
  v_created integer := 0;
  v_skipped integer := 0;
  v_daily_task_count integer;
  v_questions_per_task integer;
  v_review_target integer;
  v_needed integer;
  v_daily_cap integer;
  v_kindergarten boolean := false;
begin
  select c.* into v_child from edulock.children c where c.child_user_id = p_user_id for update;
  if not found then raise exception 'No linked child profile was found'; end if;
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_child.id::text, 0));

  select s.* into v_config from edulock.child_daily_task_settings s where s.child_id = v_child.id;
  if not found or not coalesce(v_config.enabled, false) then
    return jsonb_build_object('enabled', false, 'created_count', 0, 'date', v_today);
  end if;
  select p.* into v_preferences from edulock.child_learning_preferences p where p.child_id = v_child.id;
  v_kindergarten := coalesce(v_preferences.kindergarten_mode, false)
    or coalesce(v_child.age_years, 8) <= 6 or coalesce(v_child.school_grade, 1) <= 0;
  v_daily_task_count := case when v_kindergarten then least(v_config.daily_task_count, 2) else v_config.daily_task_count end;
  v_questions_per_task := case when v_kindergarten then least(v_config.questions_per_task, 4) else v_config.questions_per_task end;
  v_review_target := least(v_questions_per_task / 2, greatest(v_questions_per_task - 1, 0));
  v_daily_cap := least(v_config.daily_cap_minutes, v_child.daily_cap_minutes);

  if exists (
    select 1 from edulock.tasks t where t.child_id = v_child.id
      and t.title like 'Auto task • %'
      and t.created_at >= v_day_start and t.created_at < v_day_start + interval '1 day'
  ) then
    return jsonb_build_object('enabled', true, 'created_count', 0, 'skipped_count', 0,
      'date', v_today, 'legacy_tasks_preserved', true, 'kindergarten_mode', v_kindergarten);
  end if;

  select array_agg(distinct cs.subject_id order by cs.subject_id) into v_subject_ids
  from edulock.child_subjects cs join edulock.subjects s on s.id = cs.subject_id
  where cs.child_id = v_child.id and s.family_id = v_child.family_id
    and s.grade_level = v_child.school_grade
    and (s.country_code = v_child.country_code or s.country_code = 'ZZ');
  if coalesce(cardinality(v_subject_ids), 0) = 0 then
    select array_agg(s.id order by s.id) into v_subject_ids from edulock.subjects s
    where s.family_id = v_child.family_id and s.grade_level = v_child.school_grade
      and (s.country_code = v_child.country_code or s.country_code = 'ZZ')
      and (coalesce(v_child.study_track, 'none') <> 'none' or lower(s.title) not like '%religious education%');
  end if;
  if coalesce(cardinality(v_subject_ids), 0) = 0 then
    return jsonb_build_object('enabled', true, 'created_count', 0, 'skipped_count', 0,
      'date', v_today, 'kindergarten_mode', v_kindergarten,
      'message', 'No subjects match the child profile yet.');
  end if;

  select array_agg(distinct tq.question_id) into v_already_today
  from edulock.task_questions tq join edulock.tasks t on t.id = tq.task_id
  where t.child_id = v_child.id and t.auto_task_date = v_today;

  for v_task_number in 1..v_daily_task_count loop
    if exists (select 1 from edulock.tasks t where t.child_id = v_child.id
      and t.auto_task_date = v_today and t.auto_task_number = v_task_number) then
      continue;
    end if;
    v_review_ids := null; v_fresh_ids := null; v_fill_ids := null; v_question_ids := null;

    if v_review_target > 0 then
      select array_agg(c.question_id order by c.priority desc, c.question_id) into v_review_ids
      from (
        select candidate.question_id, candidate.priority
        from edulock.adaptive_question_candidates(v_child.id, v_subject_ids, v_child.school_grade, v_child.age_years, 100) candidate
        where candidate.reason in ('focus_on_weak_concept', 'spaced_review')
          and not (candidate.question_id = any(coalesce(v_already_today, array[]::uuid[])))
        order by candidate.priority desc, candidate.question_id limit v_review_target
      ) c;
    end if;

    v_question_ids := coalesce(v_review_ids, array[]::uuid[]);
    v_needed := greatest(v_questions_per_task - coalesce(cardinality(v_question_ids), 0), 0);
    if v_needed > 0 then
      select array_agg(fresh.id order by fresh.created_at desc, fresh.id) into v_fresh_ids
      from (
        select q.id, q.created_at from edulock.questions q
        where q.subject_id = any(v_subject_ids) and q.learning_mode = 'written'
          and q.grade_level = v_child.school_grade and v_child.age_years between q.age_min and q.age_max
          and not (q.id = any(coalesce(v_already_today, array[]::uuid[])))
          and not (q.id = any(coalesce(v_question_ids, array[]::uuid[])))
          and not exists (
            select 1 from edulock.attempt_answers aa join edulock.attempts a on a.id = aa.attempt_id
            where a.child_id = v_child.id and aa.question_id = q.id
          )
        order by q.created_at desc, q.id limit v_needed
      ) fresh;
      v_question_ids := coalesce(v_question_ids, array[]::uuid[]) || coalesce(v_fresh_ids, array[]::uuid[]);
    end if;

    v_needed := greatest(v_questions_per_task - coalesce(cardinality(v_question_ids), 0), 0);
    if v_needed > 0 then
      select array_agg(candidate.question_id order by candidate.priority desc, candidate.question_id) into v_fill_ids
      from (
        select c.question_id, c.priority
        from edulock.adaptive_question_candidates(v_child.id, v_subject_ids, v_child.school_grade, v_child.age_years, 100) c
        where not (c.question_id = any(coalesce(v_already_today, array[]::uuid[])))
          and not (c.question_id = any(coalesce(v_question_ids, array[]::uuid[])))
        order by c.priority desc, c.question_id limit v_needed
      ) candidate;
      v_question_ids := coalesce(v_question_ids, array[]::uuid[]) || coalesce(v_fill_ids, array[]::uuid[]);
    end if;

    v_needed := greatest(v_questions_per_task - coalesce(cardinality(v_question_ids), 0), 0);
    if v_needed > 0 then
      select array_agg(fallback.id order by fallback.recently_answered, fallback.created_at desc, fallback.id) into v_fill_ids
      from (
        select q.id, q.created_at, case when exists (
          select 1 from edulock.attempt_answers aa join edulock.attempts a on a.id = aa.attempt_id
          where a.child_id = v_child.id and aa.question_id = q.id and a.completed_at >= now() - interval '1 day'
        ) then 1 else 0 end as recently_answered
        from edulock.questions q
        where q.subject_id = any(v_subject_ids) and q.learning_mode = 'written'
          and q.grade_level = v_child.school_grade and v_child.age_years between q.age_min and q.age_max
          and not (q.id = any(coalesce(v_already_today, array[]::uuid[])))
          and not (q.id = any(coalesce(v_question_ids, array[]::uuid[])))
        order by recently_answered, q.created_at desc, q.id limit v_needed
      ) fallback;
      v_question_ids := coalesce(v_question_ids, array[]::uuid[]) || coalesce(v_fill_ids, array[]::uuid[]);
    end if;

    if coalesce(cardinality(v_question_ids), 0) < v_questions_per_task then
      v_skipped := v_skipped + 1;
      continue;
    end if;

    select q.subject_id into v_first_subject_id from edulock.questions q where q.id = v_question_ids[1];
    insert into edulock.tasks(
      family_id, child_id, subject_id, title, question_count, pass_percent, reward_minutes,
      daily_cap_minutes, active, reward_to_bank, target_grade_level, target_age_years,
      auto_task_date, auto_task_number, task_portal, kindergarten_mode
    ) values (
      v_child.family_id, v_child.id, v_first_subject_id,
      'Daily learning • ' || v_task_number || ' • ' || v_today::text,
      v_questions_per_task, v_config.pass_percent, v_config.reward_minutes, v_daily_cap,
      true, true, v_child.school_grade, v_child.age_years, v_today, v_task_number, 'written', v_kindergarten
    ) returning id into v_task_id;

    v_link_order := 0;
    foreach v_question_id in array v_question_ids loop
      insert into edulock.task_questions(task_id, question_id, sort_order)
      values (v_task_id, v_question_id, v_link_order);
      v_link_order := v_link_order + 1;
    end loop;
    v_already_today := coalesce(v_already_today, array[]::uuid[]) || v_question_ids;
    v_created := v_created + 1;
  end loop;

  return jsonb_build_object(
    'enabled', true, 'created_count', v_created, 'skipped_count', v_skipped, 'date', v_today,
    'daily_task_count', v_daily_task_count, 'questions_per_task', v_questions_per_task,
    'kindergarten_mode', v_kindergarten, 'review_question_slots', v_review_target,
    'prefers_new_saved_questions', true
  );
end;
$dailyfix$;
revoke all on function edulock.ensure_daily_tasks_for_child(uuid) from public, anon, authenticated;
grant execute on function edulock.ensure_daily_tasks_for_child(uuid) to service_role;

create or replace function edulock.grade_and_award_task(
  p_user_id uuid, p_task_id uuid, p_answers jsonb
) returns jsonb
language plpgsql security definer set search_path = ''
as $grade$
declare
  v_child edulock.children%rowtype;
  v_task edulock.tasks%rowtype;
  v_question edulock.questions%rowtype;
  v_answer jsonb;
  v_candidate jsonb;
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
  v_accuracy integer;
  v_spelling integer;
  v_final integer;
  v_keyword_total integer := 0;
  v_keyword_matched integer := 0;
  v_word_total integer := 0;
  v_word_matched integer := 0;
  v_spelling_sum numeric := 0;
  v_normalized_expected text := '';
  v_normalized_actual text := '';
  v_details jsonb := '[]'::jsonb;
  v_errors jsonb;
  v_feedback text;
  v_concept text;
  v_focus numeric;
  v_old_attempts integer;
  v_old_correct integer;
  v_old_wrong integer;
  v_streak edulock.child_task_streaks%rowtype;
  v_streak_bonus integer := 0;
  v_yesterday date := (now() at time zone 'UTC')::date - 1;
  v_today_date date := (now() at time zone 'UTC')::date;
begin
  if jsonb_typeof(p_answers) <> 'array' then raise exception 'Answers must be an array'; end if;
  if jsonb_array_length(p_answers) > 500 then raise exception 'Too many answers'; end if;

  select c.* into v_child from edulock.children c where c.child_user_id = p_user_id;
  if not found then raise exception 'No linked child profile was found'; end if;
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_child.id::text, 0));

  select t.* into v_task from edulock.tasks t
    where t.id = p_task_id and t.child_id = v_child.id and t.active = true;
  if not found then raise exception 'Task is unavailable for this child'; end if;
  if v_task.reward_package_name is not null and not exists (
    select 1 from edulock.allowed_apps aa
    where aa.child_id = v_child.id and aa.package_name = v_task.reward_package_name and aa.enabled = true
  ) then raise exception 'The selected reward app is no longer enabled for this child'; end if;
  if v_task.reward_group_id is not null and not exists (
    select 1 from edulock.app_groups g
    join edulock.app_group_apps ga on ga.group_id = g.id and ga.child_id = g.child_id
    join edulock.allowed_apps aa on aa.child_id = ga.child_id and aa.package_name = ga.package_name
    where g.id = v_task.reward_group_id and g.child_id = v_child.id and aa.enabled = true
  ) then raise exception 'The selected reward app group has no enabled apps for this child'; end if;
  if not exists (select 1 from edulock.task_questions tq where tq.task_id = v_task.id)
     and v_task.subject_id is null then raise exception 'This task has no subject or linked questions'; end if;

  select count(*) into v_total from (
    select q.id from edulock.questions q
    where q.learning_mode = 'written'
      and q.grade_level = v_task.target_grade_level
      and v_task.target_age_years between q.age_min and q.age_max
      and (q.id in (select tq.question_id from edulock.task_questions tq where tq.task_id = v_task.id)
        or (not exists (select 1 from edulock.task_questions tq where tq.task_id = v_task.id)
          and q.subject_id = v_task.subject_id))
    order by q.created_at, q.id limit v_task.question_count
  ) chosen;
  if v_total < 1 then raise exception 'This task has no written questions yet'; end if;
  if jsonb_array_length(p_answers) <> v_total then raise exception 'Please answer every question before submitting'; end if;

  for v_question in
    select q.* from edulock.questions q
    where q.learning_mode = 'written'
      and q.grade_level = v_task.target_grade_level
      and v_task.target_age_years between q.age_min and q.age_max
      and (q.id in (select tq.question_id from edulock.task_questions tq where tq.task_id = v_task.id)
        or (not exists (select 1 from edulock.task_questions tq where tq.task_id = v_task.id)
          and q.subject_id = v_task.subject_id))
    order by q.created_at, q.id limit v_task.question_count
  loop
    select count(*) into v_matches from jsonb_array_elements(p_answers) item
      where item ->> 'question_id' = v_question.id::text;
    if v_matches <> 1 then raise exception 'Each question must have exactly one answer'; end if;
    select item into v_answer from jsonb_array_elements(p_answers) item
      where item ->> 'question_id' = v_question.id::text limit 1;

    v_lang := coalesce(nullif(v_answer ->> 'language_code',''),v_question.language_code);
    if char_length(v_lang) > 16 then raise exception 'Invalid language code'; end if;
    v_actual := coalesce(v_answer ->> 'answer','');
    if char_length(v_actual) > 10000 then raise exception 'Answer is too long'; end if;
    v_accept := null;
    select qt.accepted_answers into v_accept from edulock.question_translations qt
      where qt.question_id = v_question.id and qt.language_code = v_lang;
    v_expected := '';
    v_is_correct := false;
    if v_accept is not null and jsonb_typeof(v_accept) = 'array' then
      for v_candidate in select value from jsonb_array_elements(v_accept)
      loop
        v_expected := coalesce(v_candidate #>> '{}','');
        if v_expected <> '' and
          regexp_replace(lower(btrim(v_expected)), '\s+', ' ', 'g') =
          regexp_replace(lower(btrim(v_actual)), '\s+', ' ', 'g') then
          v_is_correct := true; exit;
        end if;
      end loop;
    else
      v_expected := coalesce(v_question.answer_key ->> 'answer','');
      v_is_correct := v_expected <> '' and
        regexp_replace(lower(btrim(v_expected)), '\s+', ' ', 'g') =
        regexp_replace(lower(btrim(v_actual)), '\s+', ' ', 'g');
    end if;

    -- Accuracy is a blend of exact answer, accepted-keyword coverage and edit similarity.
    -- Spelling is separately measured on normalized words; child-friendly feedback never shames.
    v_accuracy := 0; v_spelling := 0;
    v_errors := '[]'::jsonb;
    v_feedback := '';
    if length(btrim(v_actual)) = 0 then
      v_accuracy := 0; v_spelling := 0;
      v_errors := jsonb_build_array('No answer entered');
      v_feedback := 'Try writing one important word first, then build your answer.';
    elsif v_is_correct then
      v_accuracy := 100; v_spelling := 100;
      v_feedback := 'Great work! Your answer is accurate.';
    else
      v_normalized_expected := pg_catalog.lower(pg_catalog.regexp_replace(
        pg_catalog.regexp_replace(pg_catalog.btrim(v_expected), '[[:punct:]]', '', 'g'),
        '[[:space:]]+', ' ', 'g'));
      v_normalized_actual := pg_catalog.lower(pg_catalog.regexp_replace(
        pg_catalog.regexp_replace(pg_catalog.btrim(v_actual), '[[:punct:]]', '', 'g'),
        '[[:space:]]+', ' ', 'g'));
      v_accuracy := 0;
      v_keyword_total := 0;
      v_keyword_matched := 0;
      v_word_total := 0;
      v_word_matched := 0;
      v_spelling_sum := 0;

      if v_normalized_expected <> '' then
        if pg_catalog.jsonb_typeof(v_question.accepted_keywords) = 'array'
           and pg_catalog.jsonb_array_length(v_question.accepted_keywords) > 0 then
          select count(*)::integer,
                 count(*) filter (where exists (
                   select 1
                   from pg_catalog.regexp_split_to_table(v_normalized_actual, '[[:space:]]+') as actual_words(word)
                   where actual_words.word = expected_words.word
                      or (pg_catalog.length(expected_words.word) >= 4
                          and edulock.learning_text_edit_distance(actual_words.word, expected_words.word)
                              <= case when pg_catalog.length(expected_words.word) <= 6 then 1 else 2 end)
                 ))::integer
            into v_keyword_total, v_keyword_matched
          from (
            select distinct pg_catalog.lower(pg_catalog.btrim(value)) as word
            from pg_catalog.jsonb_array_elements_text(v_question.accepted_keywords)
            where pg_catalog.btrim(value) <> ''
          ) expected_words;
          if v_keyword_total > 0 then
            v_accuracy := floor(100.0 * v_keyword_matched / v_keyword_total)::integer;
          end if;
        else
          select count(*)::integer,
                 count(*) filter (where exists (
                   select 1
                   from pg_catalog.regexp_split_to_table(v_normalized_actual, '[[:space:]]+') as actual_words(word)
                   where actual_words.word = expected_words.word
                      or (pg_catalog.length(expected_words.word) >= 4
                          and edulock.learning_text_edit_distance(actual_words.word, expected_words.word)
                              <= case when pg_catalog.length(expected_words.word) <= 6 then 1 else 2 end)
                 ))::integer
            into v_word_total, v_word_matched
          from (
            select distinct word
            from pg_catalog.regexp_split_to_table(v_normalized_expected, '[[:space:]]+') as words(word)
            where word <> '' and pg_catalog.length(word) >= 2
          ) expected_words;
          if v_word_total > 0 then
            v_accuracy := floor(100.0 * v_word_matched / v_word_total)::integer;
          elsif v_normalized_actual = v_normalized_expected then
            v_accuracy := 100;
          end if;
        end if;
      end if;

      if v_normalized_expected = '' then
        v_spelling := 100;
      elsif greatest(pg_catalog.length(v_normalized_actual), pg_catalog.length(v_normalized_expected)) > 500 then
        v_spelling := 0;
      else
        select count(*)::integer,
               coalesce(sum(
                 case
                   when exists (
                     select 1
                     from pg_catalog.regexp_split_to_table(v_normalized_actual, '[[:space:]]+') as actual_words(word)
                     where actual_words.word = expected_words.word
                   ) then 100
                   else greatest(0, 100 - ceil(
                     100.0 * coalesce((
                       select min(edulock.learning_text_edit_distance(actual_words.word, expected_words.word))
                       from pg_catalog.regexp_split_to_table(v_normalized_actual, '[[:space:]]+') as actual_words(word)
                       where actual_words.word <> ''
                     ), pg_catalog.length(expected_words.word))
                     / greatest(pg_catalog.length(expected_words.word), 1)
                   )::integer)
                 end
               ), 0)
          into v_word_total, v_spelling_sum
        from (
          select distinct word
          from pg_catalog.regexp_split_to_table(v_normalized_expected, '[[:space:]]+') as words(word)
          where word <> '' and pg_catalog.length(word) >= 2
        ) expected_words;
        if v_word_total > 0 then
          v_spelling := floor(v_spelling_sum / v_word_total)::integer;
        else
          v_spelling := greatest(0, 100 - ceil(
            100.0 * edulock.learning_text_edit_distance(v_normalized_actual, v_normalized_expected)
            / greatest(pg_catalog.length(v_normalized_expected), 1)
          )::integer);
        end if;
      end if;
      if v_accuracy < 70 then v_errors := v_errors || jsonb_build_array('Check answer accuracy and key concepts'); end if;
      if v_spelling < 80 then v_errors := v_errors || jsonb_build_array('Review spelling'); end if;
      v_feedback := coalesce(nullif(v_question.feedback_hint,''),'Let us practice this idea again. Small mistakes are part of learning.');
    end if;
    v_final := floor(v_accuracy * 0.8 + v_spelling * 0.2)::integer;
    if v_final >= 80 then v_correct := v_correct + 1; end if;
    v_concept := coalesce(nullif(v_question.concept_key,''),'general');
    v_details := v_details || jsonb_build_array(jsonb_build_object(
      'question_id',v_question.id,'concept_key',v_concept,'language_code',v_lang,
      'is_correct',(v_final >= 80),'accuracy_score',v_accuracy,
      'spelling_score',v_spelling,'final_score',v_final,
      'expected_answer',v_expected,'supplied_answer',v_actual,
      'detected_errors',v_errors,'feedback',v_feedback,'subject_id',v_question.subject_id
    ));
  end loop;

  v_score := floor((v_correct::numeric * 100) / v_total)::integer;
  v_passed := v_score >= v_task.pass_percent;
  v_today := date_trunc('day', now() at time zone 'UTC') at time zone 'UTC';
  select coalesce(sum(l.minutes_delta),0)::integer + coalesce((
    select sum(b.minutes_awarded)::integer from edulock.child_reward_bank b
    where b.child_id = v_child.id and b.earned_at >= v_today),0)
    into v_daily_used from edulock.screen_time_ledger l
    where l.child_id = v_child.id and l.created_at >= v_today and l.reason not like 'Reward bank redemption:%';
  select coalesce(sum(l.minutes_delta),0)::integer + coalesce((
    select sum(b.minutes_awarded)::integer from edulock.child_reward_bank b
    join edulock.attempts ba on ba.id = b.source_attempt_id
    where b.child_id = v_child.id and ba.task_id = v_task.id and b.earned_at >= v_today),0)
    into v_task_used from edulock.screen_time_ledger l
    join edulock.attempts a on a.id = l.source_attempt_id
    where l.child_id = v_child.id and a.task_id = v_task.id and l.created_at >= v_today
      and l.reason not like 'Reward bank redemption:%';
  if v_correct > 0 then
    v_award := greatest(0, least(v_correct * v_task.reward_minutes,
      v_child.daily_cap_minutes - v_daily_used, v_task.daily_cap_minutes - v_task_used));
  end if;

  insert into edulock.attempts(task_id,child_id,answers,score_percent,passed,awarded_minutes)
    values(v_task.id,v_child.id,p_answers,v_score,v_passed,v_award) returning id into v_attempt_id;

  for v_candidate in select value from jsonb_array_elements(v_details)
  loop
    insert into edulock.attempt_answers(attempt_id,question_id,concept_key,language_code,is_correct)
    values(v_attempt_id,(v_candidate->>'question_id')::uuid,v_candidate->>'concept_key',
      v_candidate->>'language_code',(v_candidate->>'is_correct')::boolean);
    insert into edulock.attempt_answer_feedback(
      attempt_id,question_id,child_id,concept_key,supplied_answer,expected_answer,
      accuracy_score,spelling_score,final_score,detected_errors,feedback
    ) values (
      v_attempt_id,(v_candidate->>'question_id')::uuid,v_child.id,v_candidate->>'concept_key',
      coalesce(v_candidate->>'supplied_answer',''),coalesce(v_candidate->>'expected_answer',''),
      coalesce((v_candidate->>'accuracy_score')::integer,0),coalesce((v_candidate->>'spelling_score')::integer,0),
      coalesce((v_candidate->>'final_score')::integer,0),coalesce(v_candidate->'detected_errors','[]'::jsonb),
      coalesce(v_candidate->>'feedback','')
    );
    v_concept := v_candidate->>'concept_key';
    select coalesce(focus_score,0),coalesce(attempts,0),coalesce(correct_attempts,0),coalesce(wrong_attempts,0)
      into v_focus,v_old_attempts,v_old_correct,v_old_wrong
      from edulock.child_concept_progress
      where child_id=v_child.id and concept_key=v_concept
        and subject_id=(v_candidate->>'subject_id')::uuid
      for update;
    if not found then v_focus:=0;v_old_attempts:=0;v_old_correct:=0;v_old_wrong:=0; end if;
    if coalesce((v_candidate->>'is_correct')::boolean,false) then v_focus:=greatest(0,v_focus-1.5);
    else v_focus:=least(100,v_focus+2); end if;
    insert into edulock.child_concept_progress(
      child_id,concept_key,subject_id,attempts,correct_attempts,wrong_attempts,focus_score,
      last_attempt_at,next_review_at,last_question_id,updated_at
    ) values (
      v_child.id,v_concept,(v_candidate->>'subject_id')::uuid,v_old_attempts+1,
      v_old_correct + case when coalesce((v_candidate->>'is_correct')::boolean,false) then 1 else 0 end,
      v_old_wrong + case when coalesce((v_candidate->>'is_correct')::boolean,false) then 0 else 1 end,
      v_focus,now(),now() + case when v_focus >= 4 then interval '1 hour' else interval '1 day' end,
      (v_candidate->>'question_id')::uuid,now()
    ) on conflict(child_id,concept_key,subject_id) do update set
      subject_id=excluded.subject_id,attempts=edulock.child_concept_progress.attempts+1,
      correct_attempts=edulock.child_concept_progress.correct_attempts +
        case when coalesce((v_candidate->>'is_correct')::boolean,false) then 1 else 0 end,
      wrong_attempts=edulock.child_concept_progress.wrong_attempts +
        case when coalesce((v_candidate->>'is_correct')::boolean,false) then 0 else 1 end,
      focus_score=excluded.focus_score,last_attempt_at=excluded.last_attempt_at,
      next_review_at=excluded.next_review_at,last_question_id=excluded.last_question_id,updated_at=now();
  end loop;

  if v_award > 0 then
    if v_task.reward_to_bank then
      insert into edulock.child_reward_bank(child_id,source_attempt_id,minutes_awarded,minutes_remaining)
      values(v_child.id,v_attempt_id,v_award,v_award);
    else
      insert into edulock.screen_time_ledger(child_id,source_attempt_id,minutes_delta,reason,target_package_name,target_group_id)
      values(v_child.id,v_attempt_id,v_award,'Correct-answer reward: '||v_task.title,v_task.reward_package_name,v_task.reward_group_id);
    end if;
  end if;

  if v_passed then
    insert into edulock.child_task_streaks(child_id,last_passed_day,consecutive_days,last_streak_bonus_day)
      values(v_child.id,null,0,null) on conflict(child_id) do nothing;
    select * into v_streak from edulock.child_task_streaks where child_id=v_child.id for update;
    if v_streak.last_passed_day is distinct from v_today_date then
      if v_streak.last_passed_day = v_yesterday then
        update edulock.child_task_streaks set consecutive_days=least(consecutive_days+1,30),
          last_passed_day=v_today_date where child_id=v_child.id returning * into v_streak;
      else
        update edulock.child_task_streaks set consecutive_days=1,last_passed_day=v_today_date
          where child_id=v_child.id returning * into v_streak;
      end if;
    end if;
    if v_streak.last_streak_bonus_day is distinct from v_today_date then
      v_streak_bonus:=least(30,greatest(0,v_streak.consecutive_days*5),
        greatest(0,v_child.daily_cap_minutes-v_daily_used-v_award));
      if v_streak_bonus>0 then
        update edulock.child_task_streaks set last_streak_bonus_day=v_today_date where child_id=v_child.id;
        if v_task.reward_to_bank then
          insert into edulock.child_reward_bank(child_id,source_attempt_id,minutes_awarded,minutes_remaining)
            values(v_child.id,v_attempt_id,v_streak_bonus,v_streak_bonus)
          on conflict(source_attempt_id) do update set
            minutes_awarded=edulock.child_reward_bank.minutes_awarded+excluded.minutes_awarded,
            minutes_remaining=edulock.child_reward_bank.minutes_remaining+excluded.minutes_remaining;
        else
          insert into edulock.screen_time_ledger(child_id,source_attempt_id,minutes_delta,reason,target_package_name,target_group_id)
            values(v_child.id,v_attempt_id,v_streak_bonus,'Daily streak bonus',v_task.reward_package_name,v_task.reward_group_id);
        end if;
        v_award:=v_award+v_streak_bonus;
        update edulock.attempts set awarded_minutes=v_award where id=v_attempt_id;
      end if;
    end if;
  end if;

  update edulock.tasks set active = false where id = v_task.id;

  return jsonb_build_object(
    'attempt_id',v_attempt_id,'score_percent',v_score,'passed',v_passed,'awarded_minutes',v_award,
    'daily_used_minutes_after',v_daily_used+v_award,'daily_cap_minutes',v_child.daily_cap_minutes,
    'reward_to_bank',v_task.reward_to_bank,'answer_feedback',v_details
  );
end;
$grade$;

revoke all on function edulock.grade_and_award_task(uuid,uuid,jsonb) from public, anon, authenticated;
grant execute on function edulock.grade_and_award_task(uuid,uuid,jsonb) to service_role;
notify pgrst, 'reload schema';
commit;
