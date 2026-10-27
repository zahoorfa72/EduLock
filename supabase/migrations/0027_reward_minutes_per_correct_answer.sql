-- Reward minutes are per correct answer. Convert existing task maxima to an approximately equivalent per-answer rate.
update edulock.tasks
set reward_minutes = greatest(1, ceil(reward_minutes::numeric / nullif(question_count, 0))::integer)
where question_count > 0 and reward_minutes > 0;

-- Parents may optionally direct earned minutes to one installed app; NULL means general screen time.
alter table edulock.tasks add column if not exists reward_package_name text;
alter table edulock.screen_time_ledger add column if not exists target_package_name text;
create or replace function edulock.grade_and_award_task(
  p_user_id uuid, p_task_id uuid, p_answers jsonb
) returns jsonb
language plpgsql security definer set search_path = ''
as $$
declare
  v_child edulock.children%rowtype;
  v_task edulock.tasks%rowtype;
  v_question edulock.questions%rowtype;
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
begin
  if jsonb_typeof(p_answers) <> 'array' then raise exception 'Answers must be an array'; end if;
  if jsonb_array_length(p_answers) > 500 then raise exception 'Too many answers'; end if;

  select c.* into v_child from edulock.children c where c.child_user_id = p_user_id;
  if not found then raise exception 'No linked child profile was found'; end if;

  -- Serialise same-child submissions so concurrent requests cannot exceed a daily cap.
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

    v_expected := coalesce(v_question.answer_key ->> 'answer', '');
    v_actual := coalesce(v_answer ->> 'answer', '');
    if pg_catalog.regexp_replace(pg_catalog.lower(pg_catalog.btrim(v_expected)), '\s+', ' ', 'g')
       = pg_catalog.regexp_replace(pg_catalog.lower(pg_catalog.btrim(v_actual)), '\s+', ' ', 'g')
       and v_expected <> '' then
      v_correct := v_correct + 1;
    end if;
  end loop;

  v_score := floor((v_correct::numeric * 100) / v_total)::integer;
  v_passed := v_score >= v_task.pass_percent;
  v_today := pg_catalog.date_trunc('day', now() at time zone 'UTC') at time zone 'UTC';

  select coalesce(sum(l.minutes_delta), 0)::integer into v_daily_used
    from edulock.screen_time_ledger l
    where l.child_id = v_child.id and l.created_at >= v_today;

  select coalesce(sum(l.minutes_delta), 0)::integer into v_task_used
    from edulock.screen_time_ledger l
    join edulock.attempts a on a.id = l.source_attempt_id
    where l.child_id = v_child.id and a.task_id = v_task.id and l.created_at >= v_today;

  -- reward_minutes is the parent-set allowance for each correct answer.
  -- Correct answers earn time even when the overall passing threshold is not met.
  -- Both the child-wide and per-task daily caps remain enforced transactionally.
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

  if v_award > 0 then
    insert into edulock.screen_time_ledger(child_id, source_attempt_id, minutes_delta, reason, target_package_name)
      values (v_child.id, v_attempt_id, v_award, 'Correct-answer reward: ' || v_task.title, v_task.reward_package_name);
  end if;

  return jsonb_build_object(
    'attempt_id', v_attempt_id,
    'score_percent', v_score,
    'passed', v_passed,
    'awarded_minutes', v_award,
    'daily_used_minutes_after', v_daily_used + v_award,
    'daily_cap_minutes', v_child.daily_cap_minutes
  );
end;
$$;
revoke all on function edulock.grade_and_award_task(uuid, uuid, jsonb) from public, anon, authenticated;
grant execute on function edulock.grade_and_award_task(uuid, uuid, jsonb) to service_role;

notify pgrst, 'reload schema';
