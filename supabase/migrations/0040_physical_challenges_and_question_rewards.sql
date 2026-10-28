-- Activity challenges, database-backed (not Supabase Storage) photo/video evidence,
-- and question/group-specific rewards.
begin;

alter table edulock.questions
  add column if not exists reward_minutes integer;
alter table edulock.questions drop constraint if exists questions_reward_minutes_check;
alter table edulock.questions add constraint questions_reward_minutes_check
  check (reward_minutes is null or reward_minutes between 0 and 1440);
grant update (reward_minutes) on edulock.questions to authenticated;

create table if not exists edulock.question_reward_groups (
  id uuid primary key default gen_random_uuid(),
  subject_id uuid not null references edulock.subjects(id) on delete cascade,
  concept_key text not null,
  reward_minutes integer not null default 0 check (reward_minutes between 0 and 1440),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(subject_id, concept_key)
);
alter table edulock.question_reward_groups enable row level security;
revoke all on edulock.question_reward_groups from anon;
grant select, insert, update, delete on edulock.question_reward_groups to authenticated;
drop policy if exists question_reward_groups_parent_manage on edulock.question_reward_groups;
create policy question_reward_groups_parent_manage on edulock.question_reward_groups
  for all to authenticated
  using (
    exists (
      select 1 from edulock.subjects s
      join edulock.family_profiles f on f.id = s.family_id
      join edulock.user_profiles u on u.user_id = auth.uid() and u.account_role = 'parent'
      where s.id = subject_id and f.owner_user_id = auth.uid()
    )
  )
  with check (
    exists (
      select 1 from edulock.subjects s
      join edulock.family_profiles f on f.id = s.family_id
      join edulock.user_profiles u on u.user_id = auth.uid() and u.account_role = 'parent'
      where s.id = subject_id and f.owner_user_id = auth.uid()
    )
  );

create table if not exists edulock.physical_challenges (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references edulock.family_profiles(id) on delete cascade,
  child_id uuid references edulock.children(id) on delete cascade,
  title text not null check (char_length(btrim(title)) between 2 and 120),
  description text not null default '' check (char_length(description) <= 1000),
  reward_minutes integer not null default 0 check (reward_minutes between 0 and 1440),
  evidence_type text not null default 'either' check (evidence_type in ('photo','video','either')),
  active boolean not null default true,
  created_by uuid not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists physical_challenges_family_active_idx
  on edulock.physical_challenges(family_id, active, created_at desc);

create table if not exists edulock.physical_challenge_submissions (
  id uuid primary key default gen_random_uuid(),
  challenge_id uuid not null references edulock.physical_challenges(id) on delete cascade,
  child_id uuid not null references edulock.children(id) on delete cascade,
  evidence_type text not null check (evidence_type in ('photo','video')),
  mime_type text not null check (mime_type in ('image/jpeg','video/mp4')),
  media_base64 text not null check (char_length(media_base64) between 100 and 5000000),
  compressed_size_bytes integer not null check (compressed_size_bytes between 1 and 3750000),
  note text not null default '' check (char_length(note) <= 500),
  status text not null default 'submitted' check (status in ('submitted','approved','rejected')),
  review_note text not null default '' check (char_length(review_note) <= 500),
  reward_minutes_awarded integer not null default 0 check (reward_minutes_awarded between 0 and 1440),
  submitted_at timestamptz not null default now(),
  reviewed_at timestamptz,
  reviewed_by uuid references auth.users(id),
  created_at timestamptz not null default now()
);
create index if not exists physical_challenge_submissions_child_idx
  on edulock.physical_challenge_submissions(child_id, submitted_at desc);
create index if not exists physical_challenge_submissions_challenge_idx
  on edulock.physical_challenge_submissions(challenge_id, status, submitted_at desc);
alter table edulock.physical_challenges enable row level security;
alter table edulock.physical_challenge_submissions enable row level security;
revoke all on edulock.physical_challenges, edulock.physical_challenge_submissions from public, anon, authenticated;
grant select, insert, update, delete on edulock.physical_challenges, edulock.physical_challenge_submissions to service_role;

alter table edulock.screen_time_ledger
  add column if not exists source_challenge_submission_id uuid
  references edulock.physical_challenge_submissions(id) on delete set null;
create unique index if not exists screen_time_ledger_unique_challenge_submission
  on edulock.screen_time_ledger(source_challenge_submission_id)
  where source_challenge_submission_id is not null;

create or replace function edulock.review_physical_challenge_submission(
  p_user_id uuid, p_submission_id uuid, p_approved boolean, p_review_note text default ''
) returns jsonb
language plpgsql security definer set search_path = ''
as $$
declare
  v_role text;
  v_sub edulock.physical_challenge_submissions%rowtype;
  v_challenge edulock.physical_challenges%rowtype;
  v_child edulock.children%rowtype;
  v_owner uuid;
  v_used integer := 0;
  v_award integer := 0;
  v_today timestamptz;
begin
  select u.account_role into v_role
  from edulock.user_profiles u where u.user_id = p_user_id;
  if v_role is distinct from 'parent' then raise exception 'Parent access is required'; end if;

  select * into v_sub from edulock.physical_challenge_submissions s
  where s.id = p_submission_id for update;
  if not found then raise exception 'Submission not found'; end if;
  select * into v_challenge from edulock.physical_challenges c where c.id = v_sub.challenge_id;
  select * into v_child from edulock.children c where c.id = v_sub.child_id;
  select f.owner_user_id into v_owner from edulock.family_profiles f where f.id = v_challenge.family_id;
  if v_owner is distinct from p_user_id then raise exception 'You cannot review another family submission'; end if;

  if v_sub.status <> 'submitted' then
    if (p_approved and v_sub.status = 'approved') or (not p_approved and v_sub.status = 'rejected') then
      return jsonb_build_object('submission_id', v_sub.id, 'status', v_sub.status,
        'reward_minutes_awarded', v_sub.reward_minutes_awarded, 'already_reviewed', true);
    end if;
    raise exception 'This submission has already been reviewed';
  end if;

  v_today := pg_catalog.date_trunc('day', now() at time zone 'UTC') at time zone 'UTC';
  if p_approved then
    select coalesce(sum(l.minutes_delta), 0)::integer into v_used
    from edulock.screen_time_ledger l
    where l.child_id = v_child.id and l.created_at >= v_today;
    v_award := greatest(0, least(v_challenge.reward_minutes, v_child.daily_cap_minutes - v_used));
    update edulock.physical_challenge_submissions
      set status = 'approved', review_note = left(coalesce(p_review_note,''),500),
          reward_minutes_awarded = v_award, reviewed_at = now(), reviewed_by = p_user_id
      where id = v_sub.id;
    if v_award > 0 then
      insert into edulock.screen_time_ledger(
        child_id, source_attempt_id, source_challenge_submission_id,
        minutes_delta, reason, target_package_name, target_group_id
      ) values (
        v_child.id, null, v_sub.id, v_award,
        'Parent-approved physical challenge: ' || v_challenge.title, null, null
      ) on conflict (source_challenge_submission_id) where source_challenge_submission_id is not null do nothing;
    end if;
  else
    update edulock.physical_challenge_submissions
      set status = 'rejected', review_note = left(coalesce(p_review_note,''),500),
          reward_minutes_awarded = 0, reviewed_at = now(), reviewed_by = p_user_id
      where id = v_sub.id;
  end if;
  return jsonb_build_object('submission_id', v_sub.id,
    'status', case when p_approved then 'approved' else 'rejected' end,
    'reward_minutes_awarded', v_award, 'daily_cap_minutes', v_child.daily_cap_minutes);
end;
$$;
revoke all on function edulock.review_physical_challenge_submission(uuid,uuid,boolean,text) from public, anon, authenticated;
grant execute on function edulock.review_physical_challenge_submission(uuid,uuid,boolean,text) to service_role;

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
  v_base_award integer := 0;
  v_group_award integer := 0;
  v_group record;
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

    if v_is_correct then
      v_correct := v_correct + 1;
      v_base_award := v_base_award + coalesce(v_question.reward_minutes, v_task.reward_minutes);
    end if;
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

  -- Add group bonuses only when every question in that group selected for this task is correct.
  select coalesce(sum(g.reward_minutes), 0)::integer into v_group_award
  from edulock.question_reward_groups g
  where g.subject_id = v_task.subject_id
    and exists (
      select 1
      from edulock.questions q
      where q.subject_id = g.subject_id and q.concept_key = g.concept_key
        and q.grade_level = v_task.target_grade_level
        and v_task.target_age_years between q.age_min and q.age_max
        and (
          q.id in (select tq.question_id from edulock.task_questions tq where tq.task_id = v_task.id)
          or (
            not exists (select 1 from edulock.task_questions tq where tq.task_id = v_task.id)
            and q.subject_id = v_task.subject_id
          )
        )
        and q.id in (select (d.value ->> 'question_id')::uuid from jsonb_array_elements(v_details) d(value))
    )
    and not exists (
      select 1
      from edulock.questions q
      join jsonb_to_recordset(v_details) d(question_id uuid, is_correct boolean)
        on d.question_id = q.id
      where q.subject_id = g.subject_id and q.concept_key = g.concept_key
        and q.grade_level = v_task.target_grade_level
        and v_task.target_age_years between q.age_min and q.age_max
        and (
          q.id in (select tq.question_id from edulock.task_questions tq where tq.task_id = v_task.id)
          or (
            not exists (select 1 from edulock.task_questions tq where tq.task_id = v_task.id)
            and q.subject_id = v_task.subject_id
          )
        )
        and not d.is_correct
    );

  v_award := greatest(0, least(
    v_base_award + v_group_award,
    v_child.daily_cap_minutes - v_daily_used,
    v_task.daily_cap_minutes - v_task_used
  ));

  insert into edulock.attempts(task_id, child_id, answers, score_percent, passed, awarded_minutes)
    values (v_task.id, v_child.id, p_answers, v_score, v_passed, v_award)
    returning id into v_attempt_id;

  insert into edulock.attempt_answers(attempt_id, question_id, concept_key, language_code, is_correct)
    select v_attempt_id, x.question_id, x.concept_key, x.language_code, x.is_correct
    from jsonb_to_recordset(v_details) as x(
      question_id uuid, concept_key text, language_code text, is_correct boolean
    );

  if v_award > 0 then
    insert into edulock.screen_time_ledger(child_id, source_attempt_id, minutes_delta, reason, target_package_name)
      values (v_child.id, v_attempt_id, v_award, 'Correct-answer reward: ' || v_task.title, v_task.reward_package_name);
  end if;

  -- A submitted attempt completes this assignment. Keep it from reappearing
  -- as an automatically renewing task; parents can explicitly assign a new one.
  update edulock.tasks set active = false where id = v_task.id;

  return jsonb_build_object(
    'attempt_id', v_attempt_id, 'score_percent', v_score, 'passed', v_passed,
    'awarded_minutes', v_award, 'daily_used_minutes_after', v_daily_used + v_award,
    'daily_cap_minutes', v_child.daily_cap_minutes
  );
end;
$$;

notify pgrst, 'reload schema';
commit;
