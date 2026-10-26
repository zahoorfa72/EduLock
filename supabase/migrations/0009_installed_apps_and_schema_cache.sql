-- Ensure the per-app limit column exists even if an older deployment skipped a
-- duplicate-numbered migration. Reload PostgREST's schema cache after the change.
alter table edulock.allowed_apps
  add column if not exists daily_limit_minutes integer not null default 60;

alter table edulock.allowed_apps
  drop constraint if exists allowed_apps_daily_limit_minutes_check;

alter table edulock.allowed_apps
  add constraint allowed_apps_daily_limit_minutes_check
  check (daily_limit_minutes between 1 and 1440);

grant select, insert, update, delete on edulock.allowed_apps to authenticated;

-- Parents can see only launchable apps reported by their paired child device.
create table if not exists edulock.child_installed_apps (
  id uuid primary key default gen_random_uuid(),
  child_id uuid not null references edulock.children(id) on delete cascade,
  package_name text not null,
  display_name text not null,
  last_seen_at timestamptz not null default now(),
  unique(child_id, package_name)
);

create index if not exists child_installed_apps_child_idx
  on edulock.child_installed_apps(child_id, display_name);

alter table edulock.child_installed_apps enable row level security;
grant select, insert, update, delete on edulock.child_installed_apps to authenticated;

-- Only the owning parent can read a child's installed app inventory.
drop policy if exists child_installed_apps_family_select on edulock.child_installed_apps;
drop policy if exists child_installed_apps_parent_select on edulock.child_installed_apps;
create policy child_installed_apps_parent_select on edulock.child_installed_apps
for select to authenticated
using (edulock.owns_child(child_id));

-- A paired child device can read and maintain only its own inventory.
drop policy if exists child_installed_apps_child_write on edulock.child_installed_apps;
drop policy if exists child_installed_apps_child_select on edulock.child_installed_apps;
drop policy if exists child_installed_apps_child_insert on edulock.child_installed_apps;
drop policy if exists child_installed_apps_child_update on edulock.child_installed_apps;

create policy child_installed_apps_child_select on edulock.child_installed_apps
for select to authenticated
using (exists (
  select 1 from edulock.children c
  where c.id = child_installed_apps.child_id
    and c.child_user_id = (select auth.uid())
));

create policy child_installed_apps_child_insert on edulock.child_installed_apps
for insert to authenticated
with check (exists (
  select 1 from edulock.children c
  where c.id = child_installed_apps.child_id
    and c.child_user_id = (select auth.uid())
));

create policy child_installed_apps_child_update on edulock.child_installed_apps
for update to authenticated
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

notify pgrst, 'reload schema';
