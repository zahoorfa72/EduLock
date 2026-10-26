-- EduLock installed-app inventory and schema-cache repair.
-- Safe to re-run; stays isolated in the edulock schema.
create schema if not exists edulock;

create table if not exists edulock.child_installed_apps (
  id uuid primary key default gen_random_uuid(),
  child_id uuid not null references edulock.children(id) on delete cascade,
  package_name text not null check (char_length(package_name) between 3 and 255),
  display_name text not null check (char_length(display_name) between 1 and 120),
  last_seen_at timestamptz not null default now(),
  unique (child_id, package_name)
);

create index if not exists child_installed_apps_child_name_idx
  on edulock.child_installed_apps(child_id, display_name);

alter table edulock.child_installed_apps enable row level security;
revoke all on edulock.child_installed_apps from public, anon;
grant select, insert, update, delete on edulock.child_installed_apps to authenticated;

drop policy if exists child_installed_apps_parent_select on edulock.child_installed_apps;
create policy child_installed_apps_parent_select
  on edulock.child_installed_apps for select to authenticated
  using (edulock.owns_child(child_id) and edulock.is_parent());

drop policy if exists child_installed_apps_child_insert on edulock.child_installed_apps;
create policy child_installed_apps_child_insert
  on edulock.child_installed_apps for insert to authenticated
  with check (exists (
    select 1 from edulock.children c
    where c.id = child_installed_apps.child_id
      and c.child_user_id = (select auth.uid())
  ));

drop policy if exists child_installed_apps_child_select on edulock.child_installed_apps;
create policy child_installed_apps_child_select
  on edulock.child_installed_apps for select to authenticated
  using (exists (
    select 1 from edulock.children c
    where c.id = child_installed_apps.child_id
      and c.child_user_id = (select auth.uid())
  ));

drop policy if exists child_installed_apps_child_update on edulock.child_installed_apps;
create policy child_installed_apps_child_update
  on edulock.child_installed_apps for update to authenticated
  using (exists (
    select 1 from edulock.children c
    where c.id = child_installed_apps.child_id
      and c.child_user_id = (select auth.uid())
  ))
  with check (exists (
    select 1 from edulock.children c
    where c.id = child_installed_apps.child_id
      and c.child_user_id = (select auth.uid())
  ));

-- Refresh PostgREST metadata after migrations are applied.
notify pgrst, 'reload schema';
