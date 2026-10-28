-- Enable parent-configured automatic daily tasks and restore Reward Bank payout in the
-- latest age/grade-aware task grading function. The task creator is idempotent per child/date.
begin;

create table if not exists edulock.child_daily_task_settings (
  child_id uuid primary key references edulock.children(id) on delete cascade,
  enabled boolean not null default false,
  daily_task_count integer not null default 1 check (daily_task_count between 1 and 5),
  questions_per_task integer not null default 4 check (questions_per_task between 1 and 20),
  pass_percent integer not null default 70 check (pass_percent between 0 and 100),
  reward_minutes integer not null default 1 check (reward_minutes between 0 and 60),
  daily_cap_minutes integer not null default 30 check (daily_cap_minutes between 0 and 1440),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
alter table edulock.child_daily_task_settings enable row level security;
drop policy if exists child_daily_task_settings_parent_all on edulock.child_daily_task_settings;
create policy child_daily_task_settings_parent_all on edulock.child_daily_task_settings
  for all to authenticated using (edulock.owns_child(child_id))
  with check (edulock.owns_child(child_id));
grant select, insert, update, delete on edulock.child_daily_task_settings to authenticated;

alter table edulock.tasks add column if not exists auto_task_date date;
alter table edulock.tasks add column if not exists auto_task_number integer;
create unique index if not exists tasks_one_auto_task_per_day
  on edulock.tasks(child_id, auto_task_date, auto_task_number)
  where auto_task_date is not null and auto_task_number is not null;

create or replace function edulock.ensure_daily_tasks_for_child(p_user_id uuid)
returns jsonb
language plpgsql security definer set search_path = ''
as $$
declare
  v_child edulock.children%rowtype;
  v_config edulock.child_daily_task_settings%rowtype;
  v_subject_ids uuid[];
  v_question_ids uuid[];
  v_question_id uuid;
  v_first_subject_id uuid;
  v_task_id uuid;
  v_today date := (now() at time zone 'UTC')::date;
  v_task_number integer;
  v_link_order integer;
  v_created integer := 0;
  v_skipped integer := 0;
  v_daily_cap integer;
begin
  select c.* into v_child
  from edulock.children c
  where c.child_user_id = p_user_id
  for update;
  if not found then
    raise exception 'No linked child profile was found';
  end if;
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_child.id::text, 0));

  select s.* into v_config
  from edulock.child_daily_task_settings s
  where s.child_id = v_child.id;
  if not found or not v_config.enabled then
    return jsonb_build_object('enabled', false, 'created_count', 0, 'date', v_today);
  end if;

  -- Use the subjects explicitly selected by the parent when available. Otherwise use
  -- grade/country-matched subjects already belonging to the family.
  select array_agg(cs.subject_id order by cs.subject_id)
    into v_subject_ids
  from edulock.child_subjects cs
  join edulock.subjects s on s.id = cs.subject_id
  where cs.child_id = v_child.id
    and s.family_id = v_child.family_id
    and s.grade_level = v_child.school_grade
    and (s.country_code = v_child.country_code or s.country_code = 'ZZ');

  if coalesce(cardinality(v_subject_ids), 0) = 0 then
    select array_agg(s.id order by s.id) into v_subject_ids
    from edulock.subjects s
    where s.family_id = v_child.family_id
      and s.grade_level = v_child.school_grade
      and (s.country_code = v_child.country_code or s.country_code = 'ZZ')
      and (
        coalesce(v_child.study_track, 'none') <> 'none'
        or lower(s.title) not like '%religious education%'
      );
  end if;

  if coalesce(cardinality(v_subject_ids), 0) = 0 then
    return jsonb_build_object(
      'enabled', true, 'created_count', 0, 'date', v_today,
      'message', 'No subjects match the child profile yet.'
    );
  end if;

  v_daily_cap := least(v_config.daily_cap_minutes, v_child.daily_cap_minutes);
  for v_task_number in 1..v_config.daily_task_count loop
    if exists (
      select 1 from edulock.tasks t
      where t.child_id = v_child.id
        and t.auto_task_date = v_today
        and t.auto_task_number = v_task_number
    ) then
      continue;
    end if;

    select array_agg(q.id order by q.random_order)
      into v_question_ids
    from (
      select q.id, random() as random_order
      from edulock.questions q
      where q.subject_id = any(v_subject_ids)
        and q.grade_level = v_child.school_grade
        and v_child.age_years between q.age_min and q.age_max
      order by random()
      limit v_config.questions_per_task
    ) q;

    if coalesce(cardinality(v_question_ids), 0) < v_config.questions_per_task then
      v_skipped := v_skipped + 1;
      continue;
    end if;

    select q.subject_id into v_first_subject_id
    from edulock.questions q where q.id = v_question_ids[1];

    insert into edulock.tasks(
      family_id, child_id, subject_id, title, question_count, pass_percent,
      reward_minutes, daily_cap_minutes, active, reward_to_bank,
      target_grade_level, target_age_years, auto_task_date, auto_task_number
    ) values (
      v_child.family_id, v_child.id, v_first_subject_id,
      'Daily learning • ' || v_task_number || ' • ' || v_today::text,
      v_config.questions_per_task, v_config.pass_percent, v_config.reward_minutes,
      v_daily_cap, true, true, v_child.school_grade, v_child.age_years,
      v_today, v_task_number
    ) returning id into v_task_id;

    v_link_order := 0;
    foreach v_question_id in array v_question_ids loop
      insert into edulock.task_questions(task_id, question_id, sort_order)
      values (v_task_id, v_question_id, v_link_order);
      v_link_order := v_link_order + 1;
    end loop;
    v_created := v_created + 1;
  end loop;

  return jsonb_build_object(
    'enabled', true, 'created_count', v_created, 'skipped_count', v_skipped,
    'date', v_today, 'daily_task_count', v_config.daily_task_count
  );
end;
$$;
revoke all on function edulock.ensure_daily_tasks_for_child(uuid) from public, anon, authenticated;
grant execute on function edulock.ensure_daily_tasks_for_child(uuid) to service_role;

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
  if v_task.reward_package_name is not null and not exists (
    select 1 from edulock.allowed_apps aa
    where aa.child_id = v_child.id
      and aa.package_name = v_task.reward_package_name
      and aa.enabled = true
  ) then
    raise exception 'The selected reward app is no longer enabled for this child';
  end if;
  if v_task.subject_id is null then raise exception 'This task has no subject'; end if;

  select count(*) into v_total from (
    select q.id
    from edulock.questions q
    where q.grade_level = v_task.target_grade_level
      and v_task.target_age_years between q.age_min and q.age_max
      and (
        q.id in (select tq.question_id from edulock.task_questions tq where tq.task_id = v_task.id)
        or (
          not exists (select 1 from edulock.task_questions tq where tq.task_id = v_task.id)
          and q.subject_id = v_task.subject_id
        )
      )
    order by q.created_at, q.id
    limit v_task.question_count
  ) selected_questions;
  if v_total < 1 then raise exception 'This task has no questions yet'; end if;
  if jsonb_array_length(p_answers) <> v_total then
    raise exception 'Please answer every question before submitting';
  end if;

  for v_question in
    select q.*
    from edulock.questions q
    where q.grade_level = v_task.target_grade_level
      and v_task.target_age_years between q.age_min and q.age_max
      and (
        q.id in (select tq.question_id from edulock.task_questions tq where tq.task_id = v_task.id)
        or (
          not exists (select 1 from edulock.task_questions tq where tq.task_id = v_task.id)
          and q.subject_id = v_task.subject_id
        )
      )
    order by q.created_at, q.id
    limit v_task.question_count
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

  -- Reward Bank awards count against the daily cap even though they are held in the bank
  -- instead of the screen-time ledger. Bank redemptions are not earnings and must not count twice.
  select coalesce(sum(l.minutes_delta), 0)::integer
       + coalesce((
           select sum(b.minutes_awarded)::integer
           from edulock.child_reward_bank b
           where b.child_id = v_child.id and b.earned_at >= v_today
         ), 0)
    into v_daily_used
    from edulock.screen_time_ledger l
    where l.child_id = v_child.id
      and l.created_at >= v_today
      and l.reason not like 'Reward bank redemption:%';

  select coalesce(sum(l.minutes_delta), 0)::integer
       + coalesce((
           select sum(b.minutes_awarded)::integer
           from edulock.child_reward_bank b
           join edulock.attempts bank_attempt on bank_attempt.id = b.source_attempt_id
           where b.child_id = v_child.id
             and bank_attempt.task_id = v_task.id
             and b.earned_at >= v_today
         ), 0)
    into v_task_used
    from edulock.screen_time_ledger l
    join edulock.attempts a on a.id = l.source_attempt_id
    where l.child_id = v_child.id
      and a.task_id = v_task.id
      and l.created_at >= v_today
      and l.reason not like 'Reward bank redemption:%';

  -- Parent sets reward_minutes per correct answer; partial scores earn proportionally.
  if v_correct > 0 then
    v_award := greatest(0, least(
      v_correct * v_task.reward_minutes,
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
    if v_task.reward_to_bank then
      insert into edulock.child_reward_bank(
        child_id, source_attempt_id, minutes_awarded, minutes_remaining
      ) values (v_child.id, v_attempt_id, v_award, v_award);
    else
      insert into edulock.screen_time_ledger(
        child_id, source_attempt_id, minutes_delta, reason, target_package_name, target_group_id
      ) values (
        v_child.id, v_attempt_id, v_award, 'Correct-answer reward: ' || v_task.title,
        v_task.reward_package_name, v_task.reward_group_id
      );
    end if;
  end if;

  -- A submitted attempt completes this assignment. Keep it from reappearing
  -- as an automatically renewing task; parents can explicitly assign a new one.
  update edulock.tasks set active = false where id = v_task.id;

  return jsonb_build_object(
    'attempt_id', v_attempt_id, 'score_percent', v_score, 'passed', v_passed,
    'awarded_minutes', v_award, 'daily_used_minutes_after', v_daily_used + v_award,
    'daily_cap_minutes', v_child.daily_cap_minutes,
    'reward_to_bank', v_task.reward_to_bank
  );
end;
$$;

revoke all on function edulock.grade_and_award_task(uuid, uuid, jsonb) from public, anon, authenticated;
grant execute on function edulock.grade_and_award_task(uuid, uuid, jsonb) to service_role;

notify pgrst, 'reload schema';
commit;
