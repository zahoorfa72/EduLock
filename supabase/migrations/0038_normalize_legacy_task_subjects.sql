-- Repair previously assigned mixed-subject tasks without changing their question content.
-- Existing assignments retain their declared subject; tasks without a declared subject use
-- the subject represented by the largest number of linked questions.
begin;

with ranked_subjects as (
  select
    tq.task_id,
    q.subject_id,
    count(*) as question_total,
    min(tq.sort_order) as first_order,
    row_number() over (
      partition by tq.task_id
      order by count(*) desc, min(tq.sort_order), q.subject_id
    ) as subject_rank
  from edulock.task_questions tq
  join edulock.questions q on q.id = tq.question_id
  where q.subject_id is not null
  group by tq.task_id, q.subject_id
)
update edulock.tasks t
set subject_id = rs.subject_id
from ranked_subjects rs
where rs.task_id = t.id
  and t.subject_id is null
  and rs.subject_rank = 1;

-- Deactivate only tasks that already had explicit links but no questions for their subject.
-- Older tasks with no task_questions rows still use the app's subject-bank fallback.
update edulock.tasks t
set active = false
where t.active
  and exists (select 1 from edulock.task_questions tq where tq.task_id = t.id)
  and not exists (
    select 1
    from edulock.task_questions tq
    join edulock.questions q on q.id = tq.question_id
    where tq.task_id = t.id
      and q.subject_id = t.subject_id
  );

delete from edulock.task_questions tq
using edulock.tasks t, edulock.questions q
where tq.task_id = t.id
  and tq.question_id = q.id
  and t.subject_id is not null
  and q.subject_id is distinct from t.subject_id;

-- Keep each remaining linked task's configured count aligned with its subject-only links.
with linked_counts as (
  select tq.task_id, count(*)::integer as question_total
  from edulock.task_questions tq
  group by tq.task_id
)
update edulock.tasks t
set question_count = lc.question_total
from linked_counts lc
where lc.task_id = t.id
  and lc.question_total > 0
  and t.question_count is distinct from lc.question_total;

notify pgrst, 'reload schema';
commit;
