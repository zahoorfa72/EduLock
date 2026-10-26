-- Repair/upgrade the per-app limit column for databases where older duplicate
-- 0004 migration names meant one of the files was skipped by a deployment runner.
alter table edulock.allowed_apps
  add column if not exists daily_limit_minutes integer not null default 60;

alter table edulock.allowed_apps
  drop constraint if exists allowed_apps_daily_limit_minutes_check;

alter table edulock.allowed_apps
  add constraint allowed_apps_daily_limit_minutes_check
  check (daily_limit_minutes between 1 and 1440);

grant select, insert, update, delete on edulock.allowed_apps to authenticated;
