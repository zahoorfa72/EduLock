-- Repair duplicate generated 4D starter questions while preserving attempt history,
-- translations and parent-only solutions. Generated key suffixes identify the intended seed.
begin;

create temporary table _edulock_4d_question_dedupe_map on commit drop as
with ranked as (
    select
        q.id,
        first_value(q.id) over (
            partition by q.subject_id, q.concept_key
            order by q.created_at asc, q.id asc
        ) as keeper_id,
        row_number() over (
            partition by q.subject_id, q.concept_key
            order by q.created_at asc, q.id asc
        ) as duplicate_rank
    from edulock.questions q
    where q.concept_key ~ '^(4d|3d):[^:]+:[a-z0-9-]+-[0-9]+$'
       or q.concept_key ~ '^4d:book:faith:[a-z-]+:[0-9]+$'
)
select id as duplicate_id, keeper_id
from ranked
where duplicate_rank > 1;

-- Preserve one worked solution for the canonical question before removing duplicates.
delete from edulock.question_solutions duplicate_solution
using _edulock_4d_question_dedupe_map m
where duplicate_solution.question_id = m.duplicate_id
  and exists (
      select 1 from edulock.question_solutions keeper_solution
      where keeper_solution.question_id = m.keeper_id
  );

update edulock.question_solutions duplicate_solution
set question_id = m.keeper_id,
    updated_at = greatest(duplicate_solution.updated_at, now())
from _edulock_4d_question_dedupe_map m
where duplicate_solution.question_id = m.duplicate_id
  and not exists (
      select 1 from edulock.question_solutions keeper_solution
      where keeper_solution.question_id = m.keeper_id
  );

-- Keep the canonical translation for each language, moving any language the keeper lacks.
delete from edulock.question_translations duplicate_translation
using _edulock_4d_question_dedupe_map m
where duplicate_translation.question_id = m.duplicate_id
  and exists (
      select 1 from edulock.question_translations keeper_translation
      where keeper_translation.question_id = m.keeper_id
        and keeper_translation.language_code = duplicate_translation.language_code
  );

update edulock.question_translations duplicate_translation
set question_id = m.keeper_id,
    updated_at = greatest(duplicate_translation.updated_at, now())
from _edulock_4d_question_dedupe_map m
where duplicate_translation.question_id = m.duplicate_id
  and not exists (
      select 1 from edulock.question_translations keeper_translation
      where keeper_translation.question_id = m.keeper_id
        and keeper_translation.language_code = duplicate_translation.language_code
  );

-- A child attempt should never count the same seeded concept twice.
delete from edulock.attempt_answers duplicate_answer
using _edulock_4d_question_dedupe_map m
where duplicate_answer.question_id = m.duplicate_id
  and exists (
      select 1 from edulock.attempt_answers keeper_answer
      where keeper_answer.attempt_id = duplicate_answer.attempt_id
        and keeper_answer.question_id = m.keeper_id
  );

update edulock.attempt_answers duplicate_answer
set question_id = m.keeper_id,
    concept_key = keeper_question.concept_key
from _edulock_4d_question_dedupe_map m
join edulock.questions keeper_question on keeper_question.id = m.keeper_id
where duplicate_answer.question_id = m.duplicate_id
  and not exists (
      select 1 from edulock.attempt_answers keeper_answer
      where keeper_answer.attempt_id = duplicate_answer.attempt_id
        and keeper_answer.question_id = m.keeper_id
  );

delete from edulock.questions q
using _edulock_4d_question_dedupe_map m
where q.id = m.duplicate_id;

-- Legacy starter keys are normalized without modifying parent-created concept tags.
update edulock.questions
set concept_key = regexp_replace(concept_key, '^3d:', '4d:')
where concept_key ~ '^3d:[^:]+:[a-z0-9-]+-[0-9]+$'
   or concept_key ~ '^3d:book:faith:[a-z-]+:[0-9]+$';

-- Protect future automatic seeding from duplicate rows caused by concurrent screens/retries.
create unique index if not exists questions_template_seed_identity_uidx
on edulock.questions (subject_id, concept_key)
where concept_key ~ '^(4d|3d):[^:]+:[a-z0-9-]+-[0-9]+$'
   or concept_key ~ '^4d:book:faith:[a-z-]+:[0-9]+$';

commit;
