-- Additive task enhancements: matching/picture questions, timed tasks, streak ledger
-- and privacy-minimal live device status. Existing rows remain unchanged.
begin;

alter table edulock.tasks
  add column if not exists time_limit_seconds integer
  check (time_limit_seconds is null or time_limit_seconds between 10 and 3600);

alter table edulock.questions
  add column if not exists picture_path text;

-- A child can read question images only through a signed URL issued to a linked
-- family member. The bucket is private; no public object reads are permitted.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('question-images', 'question-images', false, 5242880, array['image/jpeg','image/png','image/webp'])
on conflict (id) do update
set public = false,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

-- Paths are stored as family-id/question-id/object-name. The first path component
-- is checked against the caller's owned family; object names are opaque.
drop policy if exists edulock_question_images_parent_insert on storage.objects;
create policy edulock_question_images_parent_insert on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'question-images'
    and (storage.foldername(name))[1] ~* '^[0-9a-f-]{36}$'
    and exists (
      select 1 from edulock.families f
      where f.id::text = (storage.foldername(name))[1]
        and f.parent_user_id = (select auth.uid())
    )
  );

drop policy if exists edulock_question_images_parent_select on storage.objects;
create policy edulock_question_images_parent_select on storage.objects
  for select to authenticated
  using (
    bucket_id = 'question-images'
    and exists (
      select 1 from edulock.families f
      where f.id::text = (storage.foldername(name))[1]
        and f.parent_user_id = (select auth.uid())
    )
  );

drop policy if exists edulock_question_images_child_select on storage.objects;
create policy edulock_question_images_child_select on storage.objects
  for select to authenticated
  using (
    bucket_id = 'question-images'
    and exists (
      select 1
      from edulock.questions q
      join edulock.subjects s on s.id = q.subject_id
      join edulock.children c on c.family_id = s.family_id
      where c.child_user_id = (select auth.uid())
        and s.family_id::text = (storage.foldername(name))[1]
        and q.id::text = (storage.foldername(name))[2]
        and q.picture_path = name
    )
  );

-- Daily streak table is keyed by child and UTC day. A single transaction/function
-- should update it when a task passes; uniqueness prevents duplicate daily awards.
create table if not exists edulock.child_task_streaks (
  child_id uuid primary key references edulock.children(id) on delete cascade,
  last_passed_day date,
  consecutive_days integer not null default 0 check (consecutive_days >= 0),
  last_streak_bonus_day date
);

alter table edulock.child_task_streaks enable row level security;
drop policy if exists child_task_streaks_parent_read on edulock.child_task_streaks;
create policy child_task_streaks_parent_read on edulock.child_task_streaks
  for select to authenticated
  using (edulock.owns_child(child_id));

drop policy if exists child_task_streaks_child_read on edulock.child_task_streaks;
create policy child_task_streaks_child_read on edulock.child_task_streaks
  for select to authenticated
  using (exists (
    select 1 from edulock.children c
    where c.id = child_task_streaks.child_id and c.child_user_id = (select auth.uid())
  ));

grant select on edulock.child_task_streaks to authenticated;
revoke insert, update, delete on edulock.child_task_streaks from anon, authenticated;

-- Extend latest device status with enforcement/current-screen heartbeat fields.
alter table edulock.child_device_status
  add column if not exists device_locked boolean not null default false,
  add column if not exists guard_mode text not null default 'unknown'
    check (guard_mode in ('unknown','kiosk','accessibility','off')),
  add column if not exists current_app_package text,
  add column if not exists screen_on boolean,
  add column if not exists blocked_app_events integer not null default 0
    check (blocked_app_events >= 0);

-- Device clients may update their linked child's status via the existing RLS policy.
-- Repeated heartbeat rows update one row per child; no app-event history is stored.

create index if not exists child_task_streaks_last_passed_day_idx
  on edulock.child_task_streaks(last_passed_day);

notify pgrst, 'reload schema';
commit;
