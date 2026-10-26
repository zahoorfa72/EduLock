-- EduLock runtime repair: app inventory, app usage, location tables, limits, and Data API schema.
-- Idempotent and confined to the edulock schema; existing parent-selected rules are preserved.
create schema if not exists edulock;
grant usage on schema edulock to authenticated, service_role;

-- Per-app limits are optional by default. 1440 minutes represents effectively unrestricted daily use.
alter table edulock.allowed_apps
  add column if not exists daily_limit_minutes integer not null default 1440;
alter table edulock.allowed_apps alter column daily_limit_minutes set default 1440;
alter table edulock.allowed_apps drop constraint if exists allowed_apps_daily_limit_minutes_check;
alter table edulock.allowed_apps add constraint allowed_apps_daily_limit_minutes_check
  check (daily_limit_minutes between 1 and 1440);
grant select, insert, update, delete on edulock.allowed_apps to authenticated;

-- Inventory of user-facing apps reported from the paired child device.
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
drop policy if exists child_installed_apps_family_select on edulock.child_installed_apps;
drop policy if exists child_installed_apps_child_write on edulock.child_installed_apps;
drop policy if exists child_installed_apps_parent_select on edulock.child_installed_apps;
drop policy if exists child_installed_apps_child_select on edulock.child_installed_apps;
drop policy if exists child_installed_apps_child_insert on edulock.child_installed_apps;
drop policy if exists child_installed_apps_child_update on edulock.child_installed_apps;
create policy child_installed_apps_parent_select on edulock.child_installed_apps
  for select to authenticated
  using (edulock.is_parent() and edulock.owns_child(child_id));
create policy child_installed_apps_child_select on edulock.child_installed_apps
  for select to authenticated using (exists (
    select 1 from edulock.children c
    where c.id = child_installed_apps.child_id and c.child_user_id = (select auth.uid())
  ));
create policy child_installed_apps_child_insert on edulock.child_installed_apps
  for insert to authenticated with check (exists (
    select 1 from edulock.children c
    where c.id = child_installed_apps.child_id and c.child_user_id = (select auth.uid())
  ));
create policy child_installed_apps_child_update on edulock.child_installed_apps
  for update to authenticated using (exists (
    select 1 from edulock.children c
    where c.id = child_installed_apps.child_id and c.child_user_id = (select auth.uid())
  )) with check (exists (
    select 1 from edulock.children c
    where c.id = child_installed_apps.child_id and c.child_user_id = (select auth.uid())
  ));

-- Every newly discovered app starts enabled. The parent can then disable it or lower its limit.
create or replace function edulock.default_allow_discovered_app()
returns trigger language plpgsql security definer set search_path = ''
as $$
begin
  insert into edulock.allowed_apps(child_id, package_name, display_name, enabled, daily_limit_minutes)
  values (new.child_id, new.package_name, new.display_name, true, 1440)
  on conflict (child_id, package_name) do update set display_name = excluded.display_name;
  return new;
end;
$$;
drop trigger if exists child_installed_apps_default_allow on edulock.child_installed_apps;
create trigger child_installed_apps_default_allow
  after insert or update on edulock.child_installed_apps
  for each row execute function edulock.default_allow_discovered_app();
insert into edulock.allowed_apps(child_id, package_name, display_name, enabled, daily_limit_minutes)
select i.child_id, i.package_name, i.display_name, true, 1440
from edulock.child_installed_apps i
on conflict (child_id, package_name) do update set display_name = excluded.display_name;

-- Daily app usage summaries (no screen content, keystrokes, messages, or browser history).
create table if not exists edulock.app_usage_daily (
  id uuid primary key default gen_random_uuid(),
  child_id uuid not null references edulock.children(id) on delete cascade,
  usage_date date not null,
  package_name text not null check (char_length(package_name) between 3 and 255),
  display_name text not null check (char_length(display_name) between 1 and 120),
  used_seconds bigint not null default 0 check (used_seconds between 0 and 86400),
  updated_at timestamptz not null default now(),
  unique (child_id, usage_date, package_name)
);
create index if not exists app_usage_daily_child_date_idx
  on edulock.app_usage_daily(child_id, usage_date desc);
alter table edulock.app_usage_daily enable row level security;
revoke all on edulock.app_usage_daily from public, anon;
grant select, insert, update on edulock.app_usage_daily to authenticated;
grant select, insert, update, delete on edulock.app_usage_daily to service_role;
drop policy if exists app_usage_child_own_write on edulock.app_usage_daily;
drop policy if exists app_usage_parent_read on edulock.app_usage_daily;
drop policy if exists app_usage_daily_parent_select on edulock.app_usage_daily;
drop policy if exists app_usage_daily_child_select on edulock.app_usage_daily;
drop policy if exists app_usage_daily_child_insert on edulock.app_usage_daily;
drop policy if exists app_usage_daily_child_update on edulock.app_usage_daily;
create policy app_usage_daily_parent_select on edulock.app_usage_daily
  for select to authenticated using (edulock.is_parent() and edulock.owns_child(child_id));
create policy app_usage_daily_child_select on edulock.app_usage_daily
  for select to authenticated using (exists (
    select 1 from edulock.children c
    where c.id = app_usage_daily.child_id and c.child_user_id = (select auth.uid())
  ));
create policy app_usage_daily_child_insert on edulock.app_usage_daily
  for insert to authenticated with check (exists (
    select 1 from edulock.children c
    where c.id = app_usage_daily.child_id and c.child_user_id = (select auth.uid())
  ));
create policy app_usage_daily_child_update on edulock.app_usage_daily
  for update to authenticated using (exists (
    select 1 from edulock.children c
    where c.id = app_usage_daily.child_id and c.child_user_id = (select auth.uid())
  )) with check (exists (
    select 1 from edulock.children c
    where c.id = app_usage_daily.child_id and c.child_user_id = (select auth.uid())
  ));

-- Preserve parent-authorized, latest-point-only location sharing.
create table if not exists edulock.child_locations (
  child_id uuid primary key references edulock.children(id) on delete cascade,
  latitude double precision not null check (latitude between -90 and 90),
  longitude double precision not null check (longitude between -180 and 180),
  accuracy_meters real,
  recorded_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
alter table edulock.child_locations enable row level security;
revoke all on edulock.child_locations from public, anon;
grant select, insert, update on edulock.child_locations to authenticated;
drop policy if exists child_locations_parent_read on edulock.child_locations;
drop policy if exists child_locations_child_write on edulock.child_locations;
drop policy if exists child_locations_child_update on edulock.child_locations;
create policy child_locations_parent_read on edulock.child_locations
  for select to authenticated using (edulock.is_parent() and edulock.owns_child(child_id));
create policy child_locations_child_write on edulock.child_locations
  for insert to authenticated with check (exists (
    select 1 from edulock.children c
    where c.id = child_locations.child_id and c.child_user_id = (select auth.uid())
  ));
create policy child_locations_child_update on edulock.child_locations
  for update to authenticated using (exists (
    select 1 from edulock.children c
    where c.id = child_locations.child_id and c.child_user_id = (select auth.uid())
  )) with check (exists (
    select 1 from edulock.children c
    where c.id = child_locations.child_id and c.child_user_id = (select auth.uid())
  ));

-- Expose child device heartbeat/status. A missing heartbeat does not prove power-off.
create table if not exists edulock.child_device_status (
  child_id uuid primary key references edulock.children(id) on delete cascade,
  last_seen_at timestamptz not null default now(),
  sharing_enabled boolean not null default false,
  battery_percent integer check (battery_percent between 0 and 100),
  updated_at timestamptz not null default now()
);
create index if not exists child_device_status_last_seen_idx
  on edulock.child_device_status(last_seen_at);
alter table edulock.child_device_status enable row level security;
revoke all on edulock.child_device_status from public, anon;
grant select, insert, update on edulock.child_device_status to authenticated;
drop policy if exists child_device_status_parent_read on edulock.child_device_status;
drop policy if exists child_device_status_child_insert on edulock.child_device_status;
drop policy if exists child_device_status_child_update on edulock.child_device_status;
create policy child_device_status_parent_read on edulock.child_device_status
  for select to authenticated using (edulock.is_parent() and edulock.owns_child(child_id));
create policy child_device_status_child_insert on edulock.child_device_status
  for insert to authenticated with check (exists (
    select 1 from edulock.children c
    where c.id = child_device_status.child_id and c.child_user_id = (select auth.uid())
  ));
create policy child_device_status_child_update on edulock.child_device_status
  for update to authenticated using (exists (
    select 1 from edulock.children c
    where c.id = child_device_status.child_id and c.child_user_id = (select auth.uid())
  )) with check (exists (
    select 1 from edulock.children c
    where c.id = child_device_status.child_id and c.child_user_id = (select auth.uid())
  ));

-- Expose public and edulock to PostgREST; requests with Accept-Profile: edulock must not
-- fall back to public.children. This setting is scoped to the EduLock Supabase project.
alter role authenticator set pgrst.db_schemas = 'public, edulock';
notify pgrst, 'reload config';
notify pgrst, 'reload schema';
