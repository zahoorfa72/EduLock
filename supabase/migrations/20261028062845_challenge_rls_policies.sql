begin;

grant select, insert, update, delete on edulock.physical_challenges to authenticated;
grant select, insert on edulock.physical_challenge_submissions to authenticated;
alter table edulock.physical_challenges enable row level security;
alter table edulock.physical_challenge_submissions enable row level security;

drop policy if exists physical_challenges_parent_manage on edulock.physical_challenges;
create policy physical_challenges_parent_manage on edulock.physical_challenges
for all to authenticated
using (
  exists (
    select 1 from edulock.family_profiles f
    where f.id = physical_challenges.family_id and f.owner_user_id = auth.uid()
  )
  and exists (select 1 from edulock.user_profiles u where u.user_id=auth.uid() and u.account_role='parent')
)
with check (
  exists (
    select 1 from edulock.family_profiles f
    where f.id = physical_challenges.family_id and f.owner_user_id = auth.uid()
  )
  and created_by = auth.uid()
  and exists (select 1 from edulock.user_profiles u where u.user_id=auth.uid() and u.account_role='parent')
);

drop policy if exists physical_challenges_child_read on edulock.physical_challenges;
create policy physical_challenges_child_read on edulock.physical_challenges
for select to authenticated
using (
  active and (child_id is null or child_id in (
    select c.id from edulock.children c where c.child_user_id = auth.uid()
  ))
  and exists (select 1 from edulock.user_profiles u where u.user_id=auth.uid() and u.account_role='child')
);

drop policy if exists physical_challenge_submissions_parent_read on edulock.physical_challenge_submissions;
create policy physical_challenge_submissions_parent_read on edulock.physical_challenge_submissions
for select to authenticated
using (
  exists (
    select 1 from edulock.physical_challenges ch
    join edulock.family_profiles f on f.id=ch.family_id
    where ch.id=physical_challenge_submissions.challenge_id and f.owner_user_id=auth.uid()
  )
  and exists (select 1 from edulock.user_profiles u where u.user_id=auth.uid() and u.account_role='parent')
);

drop policy if exists physical_challenge_submissions_child_read on edulock.physical_challenge_submissions;
create policy physical_challenge_submissions_child_read on edulock.physical_challenge_submissions
for select to authenticated
using (
  child_id in (select c.id from edulock.children c where c.child_user_id=auth.uid())
);

drop policy if exists physical_challenge_submissions_child_insert on edulock.physical_challenge_submissions;
create policy physical_challenge_submissions_child_insert on edulock.physical_challenge_submissions
for insert to authenticated
with check (
  status='submitted' and reward_minutes_awarded=0 and reviewed_at is null and reviewed_by is null
  and child_id in (select c.id from edulock.children c where c.child_user_id=auth.uid())
  and exists (
    select 1 from edulock.physical_challenges ch
    where ch.id=physical_challenge_submissions.challenge_id and ch.active
      and (ch.child_id is null or ch.child_id=physical_challenge_submissions.child_id)
      and (ch.evidence_type='either' or ch.evidence_type=physical_challenge_submissions.evidence_type)
  )
);

commit;
