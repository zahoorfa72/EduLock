-- EduLock production security and trusted learning/reward operations.
-- Apply after 0001_edulock_foundation.sql in the intended Supabase project.
create table if not exists edulock.user_profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  account_role text not null check (account_role in ('parent','child')),
  display_name text not null default '',
  created_at timestamptz not null default now()
);
alter table edulock.user_profiles enable row level security;
revoke all on edulock.user_profiles from public, anon;
grant select on edulock.user_profiles to authenticated;
drop policy if exists user_profile_self_select on edulock.user_profiles;
create policy user_profile_self_select on edulock.user_profiles
  for select to authenticated using (user_id = (select auth.uid()));

create or replace function edulock.handle_new_auth_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare v_role text;
begin
  v_role := case when new.raw_user_meta_data ->> 'account_role' = 'child' then 'child' else 'parent' end;
  insert into edulock.user_profiles(user_id, account_role, display_name)
  values (new.id, v_role, coalesce(nullif(new.raw_user_meta_data ->> 'display_name',''), ''))
  on conflict (user_id) do nothing;
  return new;
end;
$$;
drop trigger if exists on_auth_user_created_edulock on auth.users;
create trigger on_auth_user_created_edulock
  after insert on auth.users
  for each row execute function edulock.handle_new_auth_user();

create or replace function edulock.is_parent()
returns boolean
language sql stable security definer set search_path = ''
as $$
  select exists(select 1 from edulock.user_profiles p
    where p.user_id = (select auth.uid()) and p.account_role = 'parent');
$$;
revoke all on function edulock.is_parent() from public;
grant execute on function edulock.is_parent() to authenticated;

alter table edulock.children
  add column if not exists child_user_id uuid unique references auth.users(id) on delete set null;
alter table edulock.children
  add column if not exists daily_cap_minutes integer not null default 60 check (daily_cap_minutes between 0 and 1440);

create table if not exists edulock.child_pairing_codes (
  id uuid primary key default gen_random_uuid(),
  child_id uuid not null references edulock.children(id) on delete cascade,
  family_id uuid not null references edulock.family_profiles(id) on delete cascade,
  created_by uuid not null references auth.users(id) on delete cascade,
  code_hash text not null unique,
  expires_at timestamptz not null,
  consumed_at timestamptz,
  created_at timestamptz not null default now()
);
create index if not exists child_pairing_codes_expiry_idx
  on edulock.child_pairing_codes(expires_at) where consumed_at is null;
alter table edulock.child_pairing_codes enable row level security;
revoke all on edulock.child_pairing_codes from public, anon, authenticated;

-- Parent-only profile creation and management. Child accounts can only read their linked profile.
drop policy if exists family_owner_all on edulock.family_profiles;
create policy family_owner_all on edulock.family_profiles for all to authenticated
  using (owner_user_id = (select auth.uid()) and edulock.is_parent())
  with check (owner_user_id = (select auth.uid()) and edulock.is_parent());

drop policy if exists children_owner_all on edulock.children;
create policy children_owner_all on edulock.children for all to authenticated
  using (edulock.owns_family(family_id) and edulock.is_parent())
  with check (edulock.owns_family(family_id) and edulock.is_parent());
drop policy if exists children_child_self_select on edulock.children;
create policy children_child_self_select on edulock.children for select to authenticated
  using (child_user_id = (select auth.uid()));

drop policy if exists subjects_owner_all on edulock.subjects;
create policy subjects_owner_all on edulock.subjects for all to authenticated
  using (edulock.owns_family(family_id) and edulock.is_parent())
  with check (edulock.owns_family(family_id) and edulock.is_parent());
drop policy if exists subjects_child_assigned_select on edulock.subjects;
create policy subjects_child_assigned_select on edulock.subjects for select to authenticated
  using (exists (
    select 1 from edulock.tasks t join edulock.children c on c.id = t.child_id
    where t.subject_id = subjects.id and t.active = true and c.child_user_id = (select auth.uid())
  ));

drop policy if exists questions_owner_all on edulock.questions;
create policy questions_owner_all on edulock.questions for all to authenticated
  using (edulock.owns_subject(subject_id) and edulock.is_parent())
  with check (edulock.owns_subject(subject_id) and edulock.is_parent());
drop policy if exists questions_child_assigned_select on edulock.questions;
create policy questions_child_assigned_select on edulock.questions for select to authenticated
  using (exists (
    select 1 from edulock.tasks t
    join edulock.children c on c.id = t.child_id
    where t.subject_id = questions.subject_id and t.active = true
      and c.child_user_id = (select auth.uid())
  ));

drop policy if exists tasks_owner_all on edulock.tasks;
create policy tasks_owner_all on edulock.tasks for all to authenticated
  using (edulock.owns_family(family_id) and edulock.is_parent())
  with check (edulock.owns_family(family_id) and edulock.is_parent() and edulock.owns_child(child_id));
drop policy if exists tasks_child_assigned_select on edulock.tasks;
create policy tasks_child_assigned_select on edulock.tasks for select to authenticated
  using (exists (
    select 1 from edulock.children c
    where c.id = tasks.child_id and c.child_user_id = (select auth.uid())
  ));

drop policy if exists attempts_owner_insert on edulock.attempts;
revoke insert, update, delete on edulock.attempts from authenticated;
drop policy if exists attempts_child_self_select on edulock.attempts;
create policy attempts_child_self_select on edulock.attempts for select to authenticated
  using (exists (
    select 1 from edulock.children c
    where c.id = attempts.child_id and c.child_user_id = (select auth.uid())
  ));

drop policy if exists apps_child_self_select on edulock.allowed_apps;
create policy apps_child_self_select on edulock.allowed_apps for select to authenticated
  using (exists (
    select 1 from edulock.children c
    where c.id = allowed_apps.child_id and c.child_user_id = (select auth.uid())
  ));
drop policy if exists ledger_child_self_select on edulock.screen_time_ledger;
create policy ledger_child_self_select on edulock.screen_time_ledger for select to authenticated
  using (exists (
    select 1 from edulock.children c
    where c.id = screen_time_ledger.child_id and c.child_user_id = (select auth.uid())
  ));

-- Column-level SELECT prevents clients, including child accounts, from reading answer keys.
revoke select on edulock.questions from authenticated;
grant select (id, subject_id, prompt, question_type, options, image_path, audio_path, created_at)
  on edulock.questions to authenticated;
grant insert, update, delete on edulock.questions to authenticated;

-- Atomic, service-role-only one-time pairing claim.
create or replace function edulock.claim_child_pairing(p_code_hash text, p_user_id uuid)
returns uuid
language plpgsql security definer set search_path = ''
as $$
declare v_pair edulock.child_pairing_codes%rowtype; v_role text;
begin
  select p.account_role into v_role from edulock.user_profiles p where p.user_id = p_user_id;
  if v_role is distinct from 'child' then raise exception 'A child account is required to link this device'; end if;

  select * into v_pair from edulock.child_pairing_codes p
    where p.code_hash = p_code_hash and p.consumed_at is null and p.expires_at > now()
    for update;
  if not found then raise exception 'Pairing code is invalid or expired'; end if;

  update edulock.children c set child_user_id = p_user_id
    where c.id = v_pair.child_id and c.family_id = v_pair.family_id and c.child_user_id is null;
  if not found then raise exception 'This child profile is already linked to an account'; end if;

  update edulock.child_pairing_codes set consumed_at = now() where id = v_pair.id;
  return v_pair.child_id;
end;
$$;
revoke all on function edulock.claim_child_pairing(text, uuid) from public, anon, authenticated;
grant execute on function edulock.claim_child_pairing(text, uuid) to service_role;

-- Grades server-side, applies the daily limits inside one transaction, then writes an immutable attempt/ledger entry.
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

  if v_award > 0 then
    insert into edulock.screen_time_ledger(child_id, source_attempt_id, minutes_delta, reason)
      values (v_child.id, v_attempt_id, v_award, 'Task reward: ' || v_task.title);
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
