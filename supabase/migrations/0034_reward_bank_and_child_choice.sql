-- Reward Bank: children earn capped minutes for correct answers and can spend them
-- on an app the parent has approved. Existing fixed app/group rewards remain supported.
begin;

alter table edulock.tasks
  add column if not exists reward_to_bank boolean not null default false;
alter table edulock.tasks
  drop constraint if exists tasks_reward_to_bank_no_fixed_target;
alter table edulock.tasks
  add constraint tasks_reward_to_bank_no_fixed_target
  check (not reward_to_bank or (reward_package_name is null and reward_group_id is null));

create table if not exists edulock.child_reward_bank (
  id uuid primary key default gen_random_uuid(),
  child_id uuid not null references edulock.children(id) on delete cascade,
  source_attempt_id uuid not null unique references edulock.attempts(id) on delete cascade,
  minutes_awarded integer not null check (minutes_awarded between 1 and 1440),
  minutes_remaining integer not null check (minutes_remaining between 0 and 1440),
  earned_at timestamptz not null default now(),
  check (minutes_remaining <= minutes_awarded)
);
create index if not exists child_reward_bank_balance_idx
  on edulock.child_reward_bank(child_id, earned_at, id)
  where minutes_remaining > 0;

alter table edulock.child_reward_bank enable row level security;
drop policy if exists child_reward_bank_parent_select on edulock.child_reward_bank;
create policy child_reward_bank_parent_select on edulock.child_reward_bank
  for select to authenticated using (edulock.owns_child(child_id));
drop policy if exists child_reward_bank_child_select on edulock.child_reward_bank;
create policy child_reward_bank_child_select on edulock.child_reward_bank
  for select to authenticated using (
    exists (
      select 1 from edulock.children c
      where c.id = child_reward_bank.child_id
        and c.child_user_id = (select auth.uid())
    )
  );
grant select on edulock.child_reward_bank to authenticated;
revoke insert, update, delete on edulock.child_reward_bank from public, anon, authenticated;

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
    where aa.child_id = v_child.id
      and aa.package_name = v_task.reward_package_name
      and aa.enabled = true
  ) then
    raise exception 'The selected reward app is no longer enabled for this child';
  end if;
  if v_task.reward_group_id is not null and not exists (
    select 1
    from edulock.app_groups g
    join edulock.app_group_apps ga on ga.group_id = g.id and ga.child_id = g.child_id
    join edulock.allowed_apps aa on aa.child_id = ga.child_id and aa.package_name = ga.package_name
    where g.id = v_task.reward_group_id
      and g.child_id = v_child.id
      and aa.enabled = true
  ) then
    raise exception 'The selected reward app group has no enabled apps for this child';
  end if;
  if not exists (select 1 from edulock.task_questions tq where tq.task_id = v_task.id)
     and v_task.subject_id is null then
    raise exception 'This task has no subject or linked questions';
  end if;

  select count(*) into v_total from (
    select q.id
    from edulock.questions q
    left join edulock.task_questions tq
      on tq.task_id = v_task.id and tq.question_id = q.id
    where tq.question_id is not null
       or (
         not exists (select 1 from edulock.task_questions tx where tx.task_id = v_task.id)
         and q.subject_id = v_task.subject_id
       )
    order by coalesce(tq.sort_order, 2147483647), q.created_at, q.id
    limit v_task.question_count
  ) selected_questions;
  if v_total < 1 then raise exception 'This task has no questions yet'; end if;
  if jsonb_array_length(p_answers) <> v_total then
    raise exception 'Please answer every question before submitting';
  end if;

  for v_question in
    select q.*
    from edulock.questions q
    left join edulock.task_questions tq
      on tq.task_id = v_task.id and tq.question_id = q.id
    where tq.question_id is not null
       or (
         not exists (select 1 from edulock.task_questions tx where tx.task_id = v_task.id)
         and q.subject_id = v_task.subject_id
       )
    order by coalesce(tq.sort_order, 2147483647), q.created_at, q.id
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

  -- Count awards, not bank redemptions: spending an earned minute must not
  -- consume the daily earning allowance a second time.
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
      )
        values (
          v_child.id, v_attempt_id, v_award, 'Correct-answer reward: ' || v_task.title,
          v_task.reward_package_name, v_task.reward_group_id
        );
    end if;
  end if;

  -- Passed tasks advance the daily streak once per UTC day. The +5-minute-per-day
  -- bonus is awarded at most once daily and remains inside the child's daily limit.
  if v_passed then
    insert into edulock.child_task_streaks(child_id, last_passed_day, consecutive_days, last_streak_bonus_day)
      values (v_child.id, null, 0, null)
      on conflict (child_id) do nothing;
    select * into v_streak
      from edulock.child_task_streaks
      where child_id = v_child.id
      for update;

    if v_streak.last_passed_day is distinct from v_today_date then
      if v_streak.last_passed_day = v_yesterday then
        update edulock.child_task_streaks
          set consecutive_days = least(consecutive_days + 1, 30),
              last_passed_day = v_today_date
          where child_id = v_child.id
          returning * into v_streak;
      else
        update edulock.child_task_streaks
          set consecutive_days = 1,
              last_passed_day = v_today_date
          where child_id = v_child.id
          returning * into v_streak;
      end if;
    end if;

    if v_streak.last_streak_bonus_day is distinct from v_today_date then
      v_streak_bonus := least(
        30,
        greatest(0, v_streak.consecutive_days * 5),
        greatest(0, v_child.daily_cap_minutes - v_daily_used - v_award)
      );
      if v_streak_bonus > 0 then
        update edulock.child_task_streaks
          set last_streak_bonus_day = v_today_date
          where child_id = v_child.id;
        if v_task.reward_to_bank then
          insert into edulock.child_reward_bank(
            child_id, source_attempt_id, minutes_awarded, minutes_remaining
          ) values (v_child.id, v_attempt_id, v_streak_bonus, v_streak_bonus)
          on conflict (source_attempt_id) do update
            set minutes_awarded = edulock.child_reward_bank.minutes_awarded + excluded.minutes_awarded,
                minutes_remaining = edulock.child_reward_bank.minutes_remaining + excluded.minutes_remaining;
        else
          insert into edulock.screen_time_ledger(
            child_id, source_attempt_id, minutes_delta, reason, target_package_name, target_group_id
          ) values (
            v_child.id, v_attempt_id, v_streak_bonus, 'Daily streak bonus',
            v_task.reward_package_name, v_task.reward_group_id
          );
        end if;
        v_award := v_award + v_streak_bonus;
        update edulock.attempts
          set awarded_minutes = v_award
          where id = v_attempt_id;
      end if;
    end if;
  end if;

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

-- Redeem bank minutes only to an enabled app explicitly approved by the parent.
-- The transaction locks the child, consumes oldest-earned minutes once, then records
-- an app-targeted ledger entry consumed by the device restriction service.
create or replace function edulock.redeem_child_reward(
  p_user_id uuid, p_package_name text, p_minutes integer
) returns jsonb
language plpgsql security definer set search_path = ''
as $$
declare
  v_child_id uuid;
  v_row record;
  v_needed integer;
  v_take integer;
  v_balance integer;
  v_app_name text;
begin
  if p_minutes is null or p_minutes < 1 or p_minutes > 1440 then
    raise exception 'Choose a reward amount from 1 to 1440 minutes';
  end if;
  if p_package_name is null or char_length(p_package_name) > 255 then
    raise exception 'Choose an approved app';
  end if;

  select c.id into v_child_id
    from edulock.children c
    where c.child_user_id = p_user_id
    for update;
  if not found then raise exception 'No linked child profile was found'; end if;

  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_child_id::text, 0));

  select aa.display_name into v_app_name
    from edulock.allowed_apps aa
    where aa.child_id = v_child_id
      and aa.package_name = p_package_name
      and aa.enabled = true;
  if not found then raise exception 'That app is not approved by your parent'; end if;

  select coalesce(sum(b.minutes_remaining), 0)::integer
    into v_balance
    from edulock.child_reward_bank b
    where b.child_id = v_child_id
      and b.minutes_remaining > 0;
  if v_balance < p_minutes then raise exception 'Not enough minutes in your Reward Bank'; end if;

  v_needed := p_minutes;
  for v_row in
    select b.id, b.minutes_remaining
      from edulock.child_reward_bank b
      where b.child_id = v_child_id and b.minutes_remaining > 0
      order by b.earned_at, b.id
      for update
  loop
    exit when v_needed <= 0;
    v_take := least(v_needed, v_row.minutes_remaining);
    update edulock.child_reward_bank
      set minutes_remaining = minutes_remaining - v_take
      where id = v_row.id;
    v_needed := v_needed - v_take;
  end loop;

  insert into edulock.screen_time_ledger(
    child_id, source_attempt_id, minutes_delta, reason, target_package_name
  ) values (
    v_child_id, null, p_minutes, 'Reward bank redemption: ' || v_app_name,
    p_package_name
  );

  return jsonb_build_object(
    'redeemed_minutes', p_minutes,
    'bank_balance_minutes', v_balance - p_minutes,
    'target_package_name', p_package_name,
    'target_app_name', v_app_name
  );
end;
$$;
revoke all on function edulock.redeem_child_reward(uuid, text, integer) from public, anon, authenticated;
grant execute on function edulock.redeem_child_reward(uuid, text, integer) to service_role;

notify pgrst, 'reload schema';
commit;
