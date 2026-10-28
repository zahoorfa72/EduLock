-- Parent-configured emergency app access and child logout authorization.
-- Codes are generated on the parent device; only salted SHA-256 verifiers are stored.
create table if not exists edulock.child_emergency_access (
  child_id uuid primary key references edulock.children(id) on delete cascade,
  family_id uuid not null references edulock.family_profiles(id) on delete cascade,
  enabled boolean not null default false,
  emergency_code_salt text not null default '',
  emergency_code_hash text not null default '',
  logout_code_salt text not null default '',
  logout_code_hash text not null default '',
  allowed_packages text[] not null default '{}',
  access_minutes integer not null default 10 check (access_minutes between 1 and 30),
  expires_at timestamptz,
  updated_at timestamptz not null default now()
);

create index if not exists child_emergency_access_family_idx
  on edulock.child_emergency_access(family_id, child_id);

alter table edulock.child_emergency_access enable row level security;
revoke all on edulock.child_emergency_access from public, anon;
grant select, insert, update on edulock.child_emergency_access to authenticated;

drop policy if exists child_emergency_access_parent_manage on edulock.child_emergency_access;
create policy child_emergency_access_parent_manage on edulock.child_emergency_access
  for all to authenticated
  using (
    edulock.is_parent()
    and edulock.owns_family(family_id)
    and edulock.owns_child(child_id)
    and exists (
      select 1 from edulock.children c
      where c.id = child_emergency_access.child_id
        and c.family_id = child_emergency_access.family_id
    )
  )
  with check (
    edulock.is_parent()
    and edulock.owns_family(family_id)
    and edulock.owns_child(child_id)
    and exists (
      select 1 from edulock.children c
      where c.id = child_emergency_access.child_id
        and c.family_id = child_emergency_access.family_id
    )
  );

drop policy if exists child_emergency_access_child_read on edulock.child_emergency_access;
create policy child_emergency_access_child_read on edulock.child_emergency_access
  for select to authenticated
  using (
    exists (
      select 1 from edulock.children c
      where c.id = child_emergency_access.child_id
        and c.child_user_id = (select auth.uid())
        and c.family_id = child_emergency_access.family_id
    )
  );
