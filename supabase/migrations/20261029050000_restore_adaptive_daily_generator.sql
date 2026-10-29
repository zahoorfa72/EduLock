-- Restore the settings-aware adaptive daily task generator.
-- The prior follow-up migration accidentally replaced this implementation with a
-- legacy daily_task_settings variant. Keep the current child_daily_task_settings
-- table, saved child subject selection, age/grade scope, and per-question review.
begin;

create or replace function edulock.ensure_daily_tasks_for_child(p_user_id uuid)
returns jsonb
language plpgsql security definer set search_path = ''
as $dailyfix$
declare
  v_child edulock.children%rowtype;
  v_config edulock.child_daily_task_settings%rowtype;
  v_preferences edulock.child_learning_preferences%rowtype;
  v_subject_ids uuid[];
  v_already_today uuid[];
  v_review_ids uuid[];
  v_fresh_ids uuid[];
  v_fill_ids uuid[];
  v_question_ids uuid[];
  v_question_id uuid;
  v_first_subject_id uuid;
  v_task_id uuid;
  v_today date := (now() at time zone 'UTC')::date;
  v_task_number integer;
  v_link_order integer;
  v_created integer := 0;
  v_skipped integer := 0;
  v_daily_task_count integer;
  v_questions_per_task integer;
  v_review_target integer;
  v_needed integer;
  v_daily_cap integer;
  v_kindergarten boolean := false;
begin
  select c.* into v_child
  from edulock.children c
  where c.child_user_id = p_user_id
  for update;
  if not found then raise exception 'No linked child profile was found'; end if;
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_child.id::text, 0));

  select s.* into v_config
  from edulock.child_daily_task_settings s
  where s.child_id = v_child.id;
  if not found or not coalesce(v_config.enabled, false) then
    return jsonb_build_object('enabled', false, 'created_count', 0, 'date', v_today);
  end if;

  select p.* into v_preferences
  from edulock.child_learning_preferences p
  where p.child_id = v_child.id;

  v_kindergarten := coalesce(v_preferences.kindergarten_mode, false)
    or coalesce(v_child.age_years, 8) <= 6
    or coalesce(v_child.school_grade, 1) <= 0;
  v_daily_task_count := case
    when v_kindergarten then least(v_config.daily_task_count, 2)
    else v_config.daily_task_count
  end;
  v_questions_per_task := case
    when v_kindergarten then least(v_config.questions_per_task, 4)
    else v_config.questions_per_task
  end;
  v_review_target := least(v_questions_per_task / 2, greatest(v_questions_per_task - 1, 0));
  v_daily_cap := least(v_config.daily_cap_minutes, v_child.daily_cap_minutes);

  -- Keep previously generated tasks; never create a duplicate task number for today.
  select array_agg(distinct tq.question_id) into v_already_today
  from edulock.task_questions tq
  join edulock.tasks t on t.id = tq.task_id
  where t.child_id = v_child.id and t.auto_task_date = v_today;

  select array_agg(distinct cs.subject_id order by cs.subject_id) into v_subject_ids
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
      and (coalesce(v_child.study_track, 'none') <> 'none'
        or lower(s.title) not like '%religious education%');
  end if;

  if coalesce(cardinality(v_subject_ids), 0) = 0 then
    return jsonb_build_object(
      'enabled', true, 'created_count', 0, 'skipped_count', 0,
      'date', v_today, 'kindergarten_mode', v_kindergarten,
      'message', 'No subjects match the child profile yet.'
    );
  end if;

  for v_task_number in 1..v_daily_task_count loop
    if exists (
      select 1 from edulock.tasks t
      where t.child_id = v_child.id
        and t.auto_task_date = v_today
        and t.auto_task_number = v_task_number
    ) then
      continue;
    end if;

    v_review_ids := null;
    v_fresh_ids := null;
    v_fill_ids := null;
    v_question_ids := null;

    -- Revisit questions from weak concepts and due spaced reviews first.
    if v_review_target > 0 then
      select array_agg(c.question_id order by c.priority desc, c.question_id)
      into v_review_ids
      from (
        select candidate.question_id, candidate.priority
        from edulock.adaptive_question_candidates(
          v_child.id, v_subject_ids, v_child.school_grade, v_child.age_years, 100
        ) candidate
        where candidate.reason in ('focus_on_weak_concept', 'spaced_review')
          and not (candidate.question_id = any(coalesce(v_already_today, array[]::uuid[])))
        order by candidate.priority desc, candidate.question_id
        limit v_review_target
      ) c;
    end if;

    v_question_ids := coalesce(v_review_ids, array[]::uuid[]);
    v_needed := greatest(v_questions_per_task - coalesce(cardinality(v_question_ids), 0), 0);

    -- Fill remaining slots with saved questions the child has not answered yet.
    if v_needed > 0 then
      select array_agg(fresh.id order by fresh.created_at desc, fresh.id)
      into v_fresh_ids
      from (
        select q.id, q.created_at
        from edulock.questions q
        where q.subject_id = any(v_subject_ids)
          and q.learning_mode = 'written'
          and q.grade_level = v_child.school_grade
          and v_child.age_years between q.age_min and q.age_max
          and not (q.id = any(coalesce(v_already_today, array[]::uuid[])))
          and not (q.id = any(coalesce(v_question_ids, array[]::uuid[])))
          and not exists (
            select 1
            from edulock.attempt_answers aa
            join edulock.attempts a on a.id = aa.attempt_id
            where a.child_id = v_child.id and aa.question_id = q.id
          )
        order by q.created_at desc, q.id
        limit v_needed
      ) fresh;
      v_question_ids := coalesce(v_question_ids, array[]::uuid[]) || coalesce(v_fresh_ids, array[]::uuid[]);
    end if;

    v_needed := greatest(v_questions_per_task - coalesce(cardinality(v_question_ids), 0), 0);
    if v_needed > 0 then
      select array_agg(candidate.question_id order by candidate.priority desc, candidate.question_id)
      into v_fill_ids
      from (
        select c.question_id, c.priority
        from edulock.adaptive_question_candidates(
          v_child.id, v_subject_ids, v_child.school_grade, v_child.age_years, 100
        ) c
        where not (c.question_id = any(coalesce(v_already_today, array[]::uuid[])))
          and not (c.question_id = any(coalesce(v_question_ids, array[]::uuid[])))
        order by c.priority desc, c.question_id
        limit v_needed
      ) candidate;
      v_question_ids := coalesce(v_question_ids, array[]::uuid[]) || coalesce(v_fill_ids, array[]::uuid[]);
    end if;

    -- If the bank is small, allow a spaced repeat but avoid repeating today's questions.
    v_needed := greatest(v_questions_per_task - coalesce(cardinality(v_question_ids), 0), 0);
    if v_needed > 0 then
      select array_agg(fallback.id order by fallback.recently_answered, fallback.created_at desc, fallback.id)
      into v_fill_ids
      from (
        select q.id, q.created_at,
          case when exists (
            select 1
            from edulock.attempt_answers aa
            join edulock.attempts a on a.id = aa.attempt_id
            where a.child_id = v_child.id
              and aa.question_id = q.id
              and a.completed_at >= now() - interval '1 day'
          ) then 1 else 0 end as recently_answered
        from edulock.questions q
        where q.subject_id = any(v_subject_ids)
          and q.learning_mode = 'written'
          and q.grade_level = v_child.school_grade
          and v_child.age_years between q.age_min and q.age_max
          and not (q.id = any(coalesce(v_already_today, array[]::uuid[])))
          and not (q.id = any(coalesce(v_question_ids, array[]::uuid[])))
        order by recently_answered, q.created_at desc, q.id
        limit v_needed
      ) fallback;
      v_question_ids := coalesce(v_question_ids, array[]::uuid[]) || coalesce(v_fill_ids, array[]::uuid[]);
    end if;

    if coalesce(cardinality(v_question_ids), 0) < v_questions_per_task then
      v_skipped := v_skipped + 1;
      continue;
    end if;

    select q.subject_id into v_first_subject_id
    from edulock.questions q where q.id = v_question_ids[1];

    insert into edulock.tasks(
      family_id, child_id, subject_id, title, question_count, pass_percent,
      reward_minutes, daily_cap_minutes, active, reward_to_bank,
      target_grade_level, target_age_years, auto_task_date, auto_task_number,
      task_portal, kindergarten_mode
    ) values (
      v_child.family_id, v_child.id, v_first_subject_id,
      'Daily learning • ' || v_task_number || ' • ' || v_today::text,
      v_questions_per_task, v_config.pass_percent, v_config.reward_minutes,
      v_daily_cap, true, true, v_child.school_grade, v_child.age_years,
      v_today, v_task_number, 'written', v_kindergarten
    ) returning id into v_task_id;

    v_link_order := 0;
    foreach v_question_id in array v_question_ids loop
      insert into edulock.task_questions(task_id, question_id, sort_order)
      values (v_task_id, v_question_id, v_link_order);
      v_link_order := v_link_order + 1;
    end loop;

    v_already_today := coalesce(v_already_today, array[]::uuid[]) || v_question_ids;
    v_created := v_created + 1;
  end loop;

  return jsonb_build_object(
    'enabled', true, 'created_count', v_created, 'skipped_count', v_skipped,
    'date', v_today, 'daily_task_count', v_daily_task_count,
    'questions_per_task', v_questions_per_task, 'kindergarten_mode', v_kindergarten,
    'review_question_slots', v_review_target, 'prefers_new_saved_questions', true,
    'practical_tasks_are_separate', true
  );
end;
$dailyfix$;

revoke all on function edulock.ensure_daily_tasks_for_child(uuid) from public, anon, authenticated;
grant execute on function edulock.ensure_daily_tasks_for_child(uuid) to service_role;

commit;
