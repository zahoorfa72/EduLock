-- Keep adaptive question selection indexed and safe for repeated deployments.
-- Existing task/grading functions and data are preserved.
begin;
create index if not exists questions_subject_mode_grade_age_created_idx
  on edulock.questions(subject_id, learning_mode, grade_level, age_min, age_max, created_at desc);
create index if not exists attempt_answers_question_attempt_idx
  on edulock.attempt_answers(question_id, attempt_id);
create index if not exists child_concept_progress_due_idx
  on edulock.child_concept_progress(child_id, next_review_at, focus_score desc);
notify pgrst, 'reload schema';
commit;
