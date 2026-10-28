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
-- Trusted practical completion and adaptive-selection helper functions.
create or replace function edulock.submit_practical_completion(
  p_user_id uuid, p_practical_task_id uuid, p_completion_note text default '', p_evidence_path text default null
) returns jsonb
language plpgsql security definer set search_path = ''
as $
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
$;
revoke all on function edulock.submit_practical_completion(uuid, uuid, text, text) from public, anon, authenticated;
grant execute on function edulock.submit_practical_completion(uuid, uuid, text, text) to service_role;

-- Select eligible saved written questions, preferring weak concepts and due review items.
-- This function returns IDs only; the caller must continue applying grade/age/subject filters.
create or replace function edulock.adaptive_question_candidates(
  p_child_id uuid, p_subject_ids uuid[], p_grade integer, p_age integer, p_limit integer default 20
) returns table(question_id uuid, concept_key text, priority numeric, reason text)
language sql stable security definer set search_path = ''
as $
  with eligible as (
    select q.id, q.concept_key, q.subject_id, q.created_at, q.difficulty_level,
      coalesce(cp.focus_score, 0)::numeric as focus_score,
      cp.next_review_at,
      cp.last_question_id
    from edulock.questions q
    left join edulock.child_concept_progress cp
      on cp.child_id = p_child_id and cp.concept_key = coalesce(nullif(q.concept_key,''),'general')
    where q.subject_id = any(p_subject_ids)
      and q.learning_mode = 'written'
      and q.grade_level = p_grade
      and p_age between q.age_min and q.age_max
      and (cp.next_review_at is null or cp.next_review_at <= now() or cp.focus_score > 0)
  )
  select e.id, coalesce(nullif(e.concept_key,''),'general'),
    (e.focus_score * 10)
      + case when e.next_review_at is not null and e.next_review_at <= now() then 5 else 0 end
      + case when e.last_question_id = e.id then 1 else 0 end
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
$;
revoke all on function edulock.adaptive_question_candidates(uuid, uuid[], integer, integer, integer) from public, anon, authenticated;
grant execute on function edulock.adaptive_question_candidates(uuid, uuid[], integer, integer, integer) to service_role;

create index if not exists questions_subject_mode_difficulty_idx
  on edulock.questions(subject_id, learning_mode, difficulty_level, created_at);

notify pgrst, 'reload schema';
commit;
