-- Adaptive practice and separate practical-task support.
-- Additive/idempotent migration; existing questions/tasks and app styling are untouched.
begin;

alter table edulock.questions
  add column if not exists learning_mode text not null default 'written'
    check (learning_mode in ('written','practical')),
  add column if not exists difficulty_level integer not null default 1
    check (difficulty_level between 1 and 5),
  add column if not exists accepted_keywords jsonb not null default '[]'::jsonb,
  add column if not exists feedback_hint text not null default '';

alter table edulock.tasks
  add column if not exists task_portal text not null default 'written'
    check (task_portal in ('written','practical')),
  add column if not exists kindergarten_mode boolean not null default false;

-- Parent-authored practical activities stay separate from the written questions pool.
create table if not exists edulock.practical_tasks (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references edulock.family_profiles(id) on delete cascade,
  child_id uuid not null references edulock.children(id) on delete cascade,
  subject_id uuid references edulock.subjects(id) on delete set null,
  title text not null check (char_length(btrim(title)) between 1 and 160),
  instructions text not null check (char_length(btrim(instructions)) between 1 and 5000),
  materials jsonb not null default '[]'::jsonb,
  safety_notes text not null default '',
  age_min integer not null default 3 check (age_min between 3 and 19),
  age_max integer not null default 19 check (age_max between age_min and 19),
  activity_kind text not null default 'practical'
    check (activity_kind in ('practical','physical','life_skill','creative')),
  expected_minutes integer not null default 10 check (expected_minutes between 1 and 180),
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists practical_tasks_child_active_idx
  on edulock.practical_tasks(child_id, active, age_min, age_max);

create table if not exists edulock.practical_task_submissions (
  id uuid primary key default gen_random_uuid(),
  practical_task_id uuid not null references edulock.practical_tasks(id) on delete cascade,
  child_id uuid not null references edulock.children(id) on delete cascade,
  completion_note text not null default '' check (char_length(completion_note) <= 2000),
  evidence_path text,
  status text not null default 'submitted'
    check (status in ('submitted','approved','needs_practice')),
  parent_feedback text not null default '' check (char_length(parent_feedback) <= 1000),
  submitted_at timestamptz not null default now(),
  reviewed_at timestamptz,
  reviewed_by uuid references auth.users(id) on delete set null
);
create index if not exists practical_submissions_child_time_idx
  on edulock.practical_task_submissions(child_id, submitted_at desc);

-- Attempt knowledge is materialized per child/concept. Wrong answers increase focus;
-- subsequent correct answers lower the focus score, while spaced-review times remain.
create table if not exists edulock.child_concept_progress (
  child_id uuid not null references edulock.children(id) on delete cascade,
  concept_key text not null check (char_length(concept_key) between 1 and 160),
  subject_id uuid not null references edulock.subjects(id) on delete cascade,
  attempts integer not null default 0 check (attempts >= 0),
  correct_attempts integer not null default 0 check (correct_attempts between 0 and attempts),
  wrong_attempts integer not null default 0 check (wrong_attempts >= 0),
  focus_score numeric(6,2) not null default 0 check (focus_score >= 0),
  last_attempt_at timestamptz,
  next_review_at timestamptz,
  last_question_id uuid references edulock.questions(id) on delete set null,
  updated_at timestamptz not null default now(),
  primary key(child_id, concept_key, subject_id)
);
create index if not exists child_concept_review_idx
  on edulock.child_concept_progress(child_id, focus_score desc, next_review_at);

create table if not exists edulock.child_learning_preferences (
  child_id uuid primary key references edulock.children(id) on delete cascade,
  kindergarten_mode boolean not null default false,
  gentle_reminders boolean not null default true,
  daily_written_question_limit integer not null default 8
    check (daily_written_question_limit between 1 and 100),
  practical_tasks_enabled boolean not null default true,
  updated_at timestamptz not null default now()
);

-- Store written-answer breakdown for parent and child progress screens.
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
create index if not exists answer_feedback_child_recent_idx
  on edulock.attempt_answer_feedback(child_id, created_at desc);
alter table edulock.attempt_answer_feedback enable row level security;
drop policy if exists answer_feedback_parent_read on edulock.attempt_answer_feedback;
create policy answer_feedback_parent_read on edulock.attempt_answer_feedback
  for select to authenticated using (edulock.owns_child(child_id));
drop policy if exists answer_feedback_child_read on edulock.attempt_answer_feedback;
create policy answer_feedback_child_read on edulock.attempt_answer_feedback
  for select to authenticated using (
    exists (select 1 from edulock.children c
      where c.id = attempt_answer_feedback.child_id and c.child_user_id = (select auth.uid()))
  );
revoke insert, update, delete on edulock.attempt_answer_feedback from public, anon, authenticated;
grant select on edulock.attempt_answer_feedback to authenticated;

-- Parent policies and child self-read policies. Writes to progress/submissions are
-- routed through trusted RPC/Edge Functions; clients cannot mint progress/approval.
alter table edulock.practical_tasks enable row level security;
alter table edulock.practical_task_submissions enable row level security;
alter table edulock.child_concept_progress enable row level security;
alter table edulock.child_learning_preferences enable row level security;

drop policy if exists practical_tasks_parent_all on edulock.practical_tasks;
create policy practical_tasks_parent_all on edulock.practical_tasks
  for all to authenticated
  using (edulock.owns_family(family_id) and edulock.is_parent())
  with check (edulock.owns_family(family_id) and edulock.is_parent()
    and edulock.owns_child(child_id));
drop policy if exists practical_tasks_child_read on edulock.practical_tasks;
create policy practical_tasks_child_read on edulock.practical_tasks
  for select to authenticated using (
    active and exists (
      select 1 from edulock.children c
      where c.id = practical_tasks.child_id and c.child_user_id = (select auth.uid())
    )
  );

drop policy if exists practical_submissions_parent_read on edulock.practical_task_submissions;
create policy practical_submissions_parent_read on edulock.practical_task_submissions
  for select to authenticated using (edulock.owns_child(child_id));
drop policy if exists practical_submissions_child_read on edulock.practical_task_submissions;
create policy practical_submissions_child_read on edulock.practical_task_submissions
  for select to authenticated using (
    exists (select 1 from edulock.children c
      where c.id = practical_task_submissions.child_id and c.child_user_id = (select auth.uid()))
  );
revoke insert, update, delete on edulock.practical_task_submissions from public, anon, authenticated;

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

drop policy if exists learning_preferences_parent_all on edulock.child_learning_preferences;
create policy learning_preferences_parent_all on edulock.child_learning_preferences
  for all to authenticated
  using (edulock.owns_child(child_id) and edulock.is_parent())
  with check (edulock.owns_child(child_id) and edulock.is_parent());
drop policy if exists learning_preferences_child_read on edulock.child_learning_preferences;
create policy learning_preferences_child_read on edulock.child_learning_preferences
  for select to authenticated using (
    exists (select 1 from edulock.children c
      where c.id = child_learning_preferences.child_id and c.child_user_id = (select auth.uid()))
  );

grant select, insert, update, delete on edulock.practical_tasks to authenticated;
grant select on edulock.practical_task_submissions, edulock.child_concept_progress to authenticated;
grant select, insert, update, delete on edulock.child_learning_preferences to authenticated;

-- Ensure a matching index is available for adaptive question selection.
-- Trusted practical completion RPC. Edge Function authenticates the caller first.
create or replace function edulock.submit_practical_completion(
  p_user_id uuid, p_practical_task_id uuid, p_completion_note text default '', p_evidence_path text default null
) returns jsonb
language plpgsql security definer set search_path = ''
as $practical$
declare
  v_child edulock.children%rowtype;
  v_task edulock.practical_tasks%rowtype;
  v_submission edulock.practical_task_submissions%rowtype;
begin
  select * into v_child from edulock.children c where c.child_user_id = p_user_id;
  if not found then raise exception 'No linked child profile was found'; end if;
  select * into v_task from edulock.practical_tasks p
    where p.id = p_practical_task_id and p.child_id = v_child.id and p.active = true;
  if not found then raise exception 'This practical task is not available to this child'; end if;
  if char_length(coalesce(p_completion_note,'')) > 2000 then raise exception 'Completion note is too long'; end if;
  if char_length(coalesce(p_evidence_path,'')) > 500 then raise exception 'Invalid evidence path'; end if;
  insert into edulock.practical_task_submissions(
    practical_task_id, child_id, completion_note, evidence_path, status
  ) values (
    v_task.id, v_child.id, coalesce(btrim(p_completion_note),''), p_evidence_path, 'submitted'
  ) returning * into v_submission;
  return jsonb_build_object(
    'submission_id', v_submission.id,
    'status', v_submission.status,
    'submitted_at', v_submission.submitted_at
  );
end;
$practical$;
revoke all on function edulock.submit_practical_completion(uuid, uuid, text, text) from public, anon, authenticated;
grant execute on function edulock.submit_practical_completion(uuid, uuid, text, text) to service_role;

-- Adaptive question selection prioritizes due reviews and weak concepts over unseen items.
create or replace function edulock.adaptive_question_candidates(
  p_child_id uuid, p_subject_ids uuid[], p_grade integer, p_age integer, p_limit integer default 20
) returns table(question_id uuid, concept_key text, priority numeric, reason text)
language sql stable security definer set search_path = ''
as $adaptive$
  with eligible as (
    select q.id, q.concept_key, q.subject_id, q.created_at, q.difficulty_level,
      coalesce(cp.focus_score, 0)::numeric as focus_score,
      cp.next_review_at
    from edulock.questions q
    left join edulock.child_concept_progress cp
      on cp.child_id = p_child_id
     and cp.subject_id = q.subject_id
     and cp.concept_key = coalesce(nullif(q.concept_key,''),'general')
    where q.subject_id = any(p_subject_ids)
      and q.learning_mode = 'written'
      and q.grade_level = p_grade
      and p_age between q.age_min and q.age_max
      and (cp.next_review_at is null or cp.next_review_at <= now() or coalesce(cp.focus_score,0) > 0)
  )
  select e.id, coalesce(nullif(e.concept_key,''),'general'),
    (e.focus_score * 10)
      + case when e.next_review_at is not null and e.next_review_at <= now() then 5 else 0 end
      + (e.difficulty_level::numeric * 0.1),
    case
      when e.focus_score > 0 then 'focus_on_weak_concept'
      when e.next_review_at is not null and e.next_review_at <= now() then 'spaced_review'
      else 'new_saved_question'
    end
  from eligible e
  order by
    (e.focus_score * 10)
      + case when e.next_review_at is not null and e.next_review_at <= now() then 5 else 0 end desc,
    e.created_at desc, e.id
  limit greatest(1, least(coalesce(p_limit,20),100));
$adaptive$;
revoke all on function edulock.adaptive_question_candidates(uuid, uuid[], integer, integer, integer) from public, anon, authenticated;
grant execute on function edulock.adaptive_question_candidates(uuid, uuid[], integer, integer, integer) to service_role;

-- Keep new saved questions discoverable without rewriting the database task generator.
create or replace function edulock.ensure_daily_tasks_for_child(p_user_id uuid)
returns jsonb
language plpgsql security definer set search_path = ''
as $daily$
declare
  v_child edulock.children%rowtype;
  v_preferences edulock.child_learning_preferences%rowtype;
  v_daily_settings record;
  v_subject record;
  v_ids uuid[];
  v_question record;
  v_task edulock.tasks%rowtype;
  v_existing_today integer := 0;
  v_created_tasks integer := 0;
  v_created_questions integer := 0;
  v_question_limit integer := 8;
  v_task_count integer := 3;
  v_daily_task_count integer := 1;
  v_pass_percent integer := 70;
  v_reward_minutes integer := 1;
  v_daily_cap integer := 30;
  v_kindergarten boolean := false;
  v_result jsonb;
  v_today_start timestamptz := date_trunc('day', now() at time zone 'UTC') at time zone 'UTC';
begin
  select * into v_child from edulock.children c where c.child_user_id = p_user_id for update;
  if not found then raise exception 'No linked child profile was found'; end if;
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_child.id::text, 0));

  select * into v_preferences
    from edulock.child_learning_preferences p where p.child_id = v_child.id;
  if found then
    v_question_limit := greatest(1, least(coalesce(v_preferences.daily_written_question_limit,8),100));
    v_kindergarten := coalesce(v_preferences.kindergarten_mode,false);
  end if;

  -- Read the existing per-child daily task configuration when present. The preferences
  -- table already supplies safe defaults for installations with no daily automation setup.
  select ds.daily_task_count, ds.questions_per_task, ds.pass_percent, ds.reward_minutes, ds.daily_cap_minutes
    into v_daily_settings
    from edulock.daily_task_settings ds where ds.child_id = v_child.id and ds.enabled = true;
  if found then
    v_daily_task_count := greatest(1, least(coalesce((v_daily_settings).daily_task_count,1),3));
    v_question_limit := greatest(1, least(coalesce((v_daily_settings).questions_per_task,4),100));
    v_pass_percent := greatest(0, least(coalesce((v_daily_settings).pass_percent,70),100));
    v_reward_minutes := greatest(0, least(coalesce((v_daily_settings).reward_minutes,1),1440));
    v_daily_cap := greatest(0, least(coalesce((v_daily_settings).daily_cap_minutes,30),1440));
  end if;
  v_kindergarten := v_kindergarten or coalesce(v_child.age_years,8) <= 6 or coalesce(v_child.school_grade,1) <= 0;
  v_task_count := case when v_kindergarten then least(v_daily_task_count,2) else least(v_daily_task_count,3) end;
  v_question_limit := case when v_kindergarten then least(v_question_limit,4) else least(v_question_limit,8) end;

  -- Do not duplicate today's auto tasks. If there are existing active tasks for today,
  -- return them as-is; the next day naturally selects newer saved questions.
  select count(*) into v_existing_today
    from edulock.tasks t
    where t.child_id = v_child.id
      and t.active = true
      and t.title like 'Auto task • %'
      and t.created_at >= v_today_start;

  if v_existing_today = 0 then
    for v_subject in
      select s.id, s.title, s.family_id
      from edulock.subjects s
      where s.family_id = v_child.family_id
        and (not exists (select 1 from edulock.child_subjects css where css.child_id = v_child.id)
          or exists (select 1 from edulock.child_subjects css where css.child_id = v_child.id and css.subject_id = s.id))
        and exists (
          select 1 from edulock.questions q
          where q.subject_id = s.id and q.learning_mode = 'written'
            and q.grade_level = v_child.school_grade
            and v_child.age_years between q.age_min and q.age_max
        )
      order by s.title
      limit v_task_count
    loop
      -- Adaptive candidate ranking prefers concepts needing attention and due review.
      select array_agg(candidate.question_id order by candidate.priority desc, candidate.question_id)
        into v_ids
        from edulock.adaptive_question_candidates(
          v_child.id, array[v_subject.id], v_child.school_grade, v_child.age_years,
          greatest(1, least(v_question_limit / greatest(v_task_count,1), 4))
        ) candidate;

      -- Newly saved eligible questions remain available even when no history exists.
      if coalesce(array_length(v_ids,1),0) = 0 then
        select array_agg(recent.id order by recent.created_at desc, recent.id)
          into v_ids
          from (
            select q.id, q.created_at
            from edulock.questions q
            where q.subject_id = v_subject.id
              and q.learning_mode = 'written'
              and q.grade_level = v_child.school_grade
              and v_child.age_years between q.age_min and q.age_max
            order by q.created_at desc, q.id
            limit greatest(1, least(v_question_limit / greatest(v_task_count,1), 4))
          ) recent;
      end if;

      if coalesce(array_length(v_ids,1),0) = 0 then continue; end if;
      insert into edulock.tasks(
        family_id, child_id, subject_id, title, question_count, pass_percent,
        reward_minutes, daily_cap_minutes, active, target_grade_level, target_age_years,
        task_portal, kindergarten_mode, reward_to_bank
      ) values (
        v_child.family_id, v_child.id, v_subject.id, left('Auto task • ' || v_subject.title,160),
        array_length(v_ids,1), v_pass_percent, v_reward_minutes, v_daily_cap, true,
        v_child.school_grade, v_child.age_years, 'written', v_kindergarten, true
      ) returning * into v_task;

      for v_question in
        select chosen.question_id, chosen.ordinality::integer as order_index
        from unnest(v_ids) with ordinality chosen(question_id, ordinality)
      loop
        insert into edulock.task_questions(task_id,question_id,sort_order)
        values(v_task.id,v_question.question_id,v_question.order_index-1)
        on conflict(task_id,question_id) do nothing;
      end loop;
      v_created_tasks := v_created_tasks + 1;
      v_created_questions := v_created_questions + array_length(v_ids,1);
    end loop;
  end if;

  select coalesce(jsonb_agg(jsonb_build_object(
      'id',t.id,'title',t.title,'question_count',t.question_count,
      'kindergarten_mode',coalesce(t.kindergarten_mode,false)
    ) order by t.created_at desc),'[]'::jsonb)
    into v_result
    from edulock.tasks t
    where t.child_id = v_child.id and t.active = true
      and t.title like 'Auto task • %'
      and t.created_at >= v_today_start;

  return jsonb_build_object(
    'created_tasks',v_created_tasks,
    'created_questions',v_created_questions,
    'kindergarten_mode',v_kindergarten,
    'tasks',coalesce(v_result,'[]'::jsonb)
  );
end;
$daily$;
revoke all on function edulock.ensure_daily_tasks_for_child(uuid) from public, anon, authenticated;
grant execute on function edulock.ensure_daily_tasks_for_child(uuid) to service_role;

create index if not exists questions_subject_mode_difficulty_idx
  on edulock.questions(subject_id, learning_mode, difficulty_level, created_at);

-- Trusted grading extension. This version retains the existing translation-aware grader,
-- records partial credit, and updates concept mastery in the same transaction.
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
      v_accuracy := case
        when lower(btrim(v_expected)) = lower(btrim(v_actual)) then 100
        when length(btrim(v_expected)) > 0 and
          (lower(btrim(v_expected)) like '%' || lower(btrim(v_actual)) || '%'
           or lower(btrim(v_actual)) like '%' || lower(btrim(v_expected)) || '%') then 70
        when coalesce(v_question.accepted_keywords,'[]'::jsonb) <> '[]'::jsonb and
          exists (select 1 from jsonb_array_elements_text(v_question.accepted_keywords) k
            where lower(v_actual) like '%' || lower(k) || '%') then 65
        else 0 end;
      v_spelling := case
        when length(btrim(v_expected)) = 0 then 100
        when lower(regexp_replace(btrim(v_actual), '[^[:alnum:] ]', '', 'g')) =
             lower(regexp_replace(btrim(v_expected), '[^[:alnum:] ]', '', 'g')) then 100
        when lower(btrim(v_actual)) = lower(btrim(v_expected)) then 100
        when length(btrim(v_actual)) > 0 and length(btrim(v_expected)) > 0
          and abs(length(btrim(v_actual)) - length(btrim(v_expected))) <= 1 then 80
        when length(btrim(v_actual)) > 0 and length(btrim(v_expected)) > 0
          and abs(length(btrim(v_actual)) - length(btrim(v_expected))) <= 3 then 60
        else 30 end;
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
      from edulock.child_concept_progress where child_id=v_child.id and concept_key=v_concept
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
    ) on conflict(child_id,concept_key) do update set
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

  return jsonb_build_object(
    'attempt_id',v_attempt_id,'score_percent',v_score,'passed',v_passed,'awarded_minutes',v_award,
    'daily_used_minutes_after',v_daily_used+v_award,'daily_cap_minutes',v_child.daily_cap_minutes,
    'reward_to_bank',v_task.reward_to_bank,'answer_feedback',v_details
  );
end;
$grade$;
revoke all on function edulock.grade_and_award_task(uuid,uuid,jsonb) from public,anon,authenticated;
grant execute on function edulock.grade_and_award_task(uuid,uuid,jsonb) to service_role;

notify pgrst, 'reload schema';
commit;
