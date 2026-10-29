-- Prefer weak concepts and due reviews, then unseen saved questions, then retention repeats.
-- Does not alter historical attempts or delete existing tasks.
begin;
create or replace function edulock.adaptive_question_candidates(
  p_child_id uuid, p_subject_ids uuid[], p_grade integer, p_age integer, p_limit integer default 20
) returns table(question_id uuid, concept_key text, priority numeric, reason text)
language sql stable security definer set search_path = ''
as $adaptive$
  with eligible as (
    select q.id, coalesce(nullif(q.concept_key,''),'general') as concept_key,
      q.subject_id, q.created_at, q.difficulty_level,
      coalesce(cp.focus_score,0)::numeric as focus_score,
      cp.next_review_at,
      not exists (
        select 1 from edulock.attempt_answers aa
        join edulock.attempts a on a.id = aa.attempt_id
        where a.child_id = p_child_id and aa.question_id = q.id
      ) as unseen
    from edulock.questions q
    left join edulock.child_concept_progress cp
      on cp.child_id = p_child_id and cp.subject_id = q.subject_id
     and cp.concept_key = coalesce(nullif(q.concept_key,''),'general')
    where q.subject_id = any(p_subject_ids)
      and q.learning_mode = 'written'
      and q.grade_level = p_grade
      and p_age between q.age_min and q.age_max
  )
  select e.id, e.concept_key,
    (e.focus_score * 10)
      + case when e.next_review_at is not null and e.next_review_at <= now() then 5 else 0 end
      + case when e.unseen then 3 else 0 end
      + (coalesce(e.difficulty_level,1)::numeric * 0.1),
    case
      when e.focus_score > 0 then 'focus_on_weak_concept'
      when e.next_review_at is not null and e.next_review_at <= now() then 'spaced_review'
      when e.unseen then 'new_saved_question'
      else 'retention_repeat'
    end
  from eligible e
  order by
    (e.focus_score * 10)
      + case when e.next_review_at is not null and e.next_review_at <= now() then 5 else 0 end
      + case when e.unseen then 3 else 0 end desc,
    e.created_at desc, e.id
  limit greatest(1, least(coalesce(p_limit,20),100));
$adaptive$;
revoke all on function edulock.adaptive_question_candidates(uuid, uuid[], integer, integer, integer) from public, anon, authenticated;
grant execute on function edulock.adaptive_question_candidates(uuid, uuid[], integer, integer, integer) to service_role;
notify pgrst, 'reload schema';
commit;
