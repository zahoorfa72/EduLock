-- Persist the subjects a parent has enabled for each child.
-- Apply this migration after 0008_repair_allowed_app_limits.sql.
create table if not exists edulock.child_subjects (
  family_id uuid not null references edulock.family_profiles(id) on delete cascade,
  child_id uuid not null references edulock.children(id) on delete cascade,
  subject_id uuid not null references edulock.subjects(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (child_id, subject_id)
);

create index if not exists child_subjects_family_child_idx
  on edulock.child_subjects(family_id, child_id);

alter table edulock.child_subjects enable row level security;
revoke all on edulock.child_subjects from public, anon;
grant select, insert, delete on edulock.child_subjects to authenticated;

drop policy if exists child_subjects_parent_manage on edulock.child_subjects;
create policy child_subjects_parent_manage on edulock.child_subjects
  for all to authenticated
  using (
    edulock.is_parent()
    and edulock.owns_family(family_id)
    and edulock.owns_child(child_id)
    and exists (
      select 1 from edulock.subjects s
      where s.id = child_subjects.subject_id
        and s.family_id = child_subjects.family_id
    )
  )
  with check (
    edulock.is_parent()
    and edulock.owns_family(family_id)
    and edulock.owns_child(child_id)
    and exists (
      select 1 from edulock.subjects s
      where s.id = child_subjects.subject_id
        and s.family_id = child_subjects.family_id
    )
  );

drop policy if exists child_subjects_child_read on edulock.child_subjects;
create policy child_subjects_child_read on edulock.child_subjects
  for select to authenticated
  using (
    exists (
      select 1 from edulock.children c
      where c.id = child_subjects.child_id
        and c.child_user_id = (select auth.uid())
    )
  );
