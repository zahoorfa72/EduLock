-- EduLock runtime schema repair: app usage reporting, schema exposure, and secure policies.
-- Safe to run repeatedly; this migration touches only the isolated edulock schema.

create schema if not exists edulock;

alter table edulock.allowed_apps
  add column if not exists daily_limit_minutes integer not null default 1440;

alter table edulock.allowed_apps
  alter column daily_limit_minutes set default 1440;

alter table edulock.allowed_apps
  drop constraint if exists allowed_apps_daily_limit_minutes_check;
alter table edulock.allowed_apps
  add constraint allowed_apps_daily_limit_minutes_check
  check (daily_limit_minutes between 1 and 1440);

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
grant select, insert, update, delete on edulock.app_usage_daily to authenticated;
grant select, insert, update, delete on edulock.app_usage_daily to service_role;

drop policy if exists app_usage_daily_parent_select on edulock.app_usage_daily;
create policy app_usage_daily_parent_select
  on edulock.app_usage_daily for select to authenticated
  using (edulock.is_parent() and edulock.owns_child(child_id));

drop policy if exists app_usage_daily_child_select on edulock.app_usage_daily;
create policy app_usage_daily_child_select
  on edulock.app_usage_daily for select to authenticated
  using (exists (
    select 1 from edulock.children c
    where c.id = app_usage_daily.child_id
      and c.child_user_id = (select auth.uid())
  ));

drop policy if exists app_usage_daily_child_insert on edulock.app_usage_daily;
create policy app_usage_daily_child_insert
  on edulock.app_usage_daily for insert to authenticated
  with check (exists (
    select 1 from edulock.children c
    where c.id = app_usage_daily.child_id
      and c.child_user_id = (select auth.uid())
  ));

drop policy if exists app_usage_daily_child_update on edulock.app_usage_daily;
create policy app_usage_daily_child_update
  on edulock.app_usage_daily for update to authenticated
  using (exists (
    select 1 from edulock.children c
    where c.id = app_usage_daily.child_id
      and c.child_user_id = (select auth.uid())
  ))
  with check (exists (
    select 1 from edulock.children c
    where c.id = app_usage_daily.child_id
      and c.child_user_id = (select auth.uid())
  ));

-- Backfill rules for inventory rows already reported by child devices.
-- New and existing apps remain allowed until the parent explicitly disables or limits them.
insert into edulock.allowed_apps(child_id, package_name, display_name, enabled, daily_limit_minutes)
select i.child_id, i.package_name, i.display_name, true, 1440
from edulock.child_installed_apps i
on conflict (child_id, package_name) do update
set display_name = excluded.display_name;

-- Expose the isolated application schema to PostgREST alongside public so requests using
-- Accept-Profile/Content-Profile: edulock do not silently target public.children.
alter role authenticator set pgrst.db_schemas = 'public, edulock';
notify pgrst, 'reload config';
notify pgrst, 'reload schema';
