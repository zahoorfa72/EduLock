begin;

-- Evidence is temporary: after the parent approves or rejects a submission,
-- erase the photo/video bytes while keeping the submission and review metadata.
alter table edulock.physical_challenge_submissions
  alter column media_base64 drop not null;
alter table edulock.physical_challenge_submissions
  drop constraint if exists physical_challenge_submissions_media_base64_check;
alter table edulock.physical_challenge_submissions
  add constraint physical_challenge_submissions_media_base64_check
  check (media_base64 is null or char_length(media_base64) between 100 and 5000000);

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
  select u.account_role into v_role from edulock.user_profiles u where u.user_id = p_user_id;
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
        'reward_minutes_awarded', v_sub.reward_minutes_awarded, 'already_reviewed', true,
        'evidence_deleted', v_sub.media_base64 is null);
    end if;
    raise exception 'This submission has already been reviewed';
  end if;

  v_today := pg_catalog.date_trunc('day', now() at time zone 'UTC') at time zone 'UTC';
  if p_approved then
    select coalesce(sum(l.minutes_delta), 0)::integer into v_used
      from edulock.screen_time_ledger l where l.child_id = v_child.id and l.created_at >= v_today;
    v_award := greatest(0, least(v_challenge.reward_minutes, v_child.daily_cap_minutes - v_used));
    update edulock.physical_challenge_submissions
      set status = 'approved', review_note = left(coalesce(p_review_note,''),500),
          reward_minutes_awarded = v_award, reviewed_at = now(), reviewed_by = p_user_id,
          media_base64 = null
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
          reward_minutes_awarded = 0, reviewed_at = now(), reviewed_by = p_user_id,
          media_base64 = null
      where id = v_sub.id;
  end if;

  return jsonb_build_object('submission_id', v_sub.id,
    'status', case when p_approved then 'approved' else 'rejected' end,
    'reward_minutes_awarded', v_award, 'daily_cap_minutes', v_child.daily_cap_minutes,
    'evidence_deleted', true);
end;
$$;

revoke all on function edulock.review_physical_challenge_submission(uuid,uuid,boolean,text) from public, anon, authenticated;
grant execute on function edulock.review_physical_challenge_submission(uuid,uuid,boolean,text) to service_role;

commit;