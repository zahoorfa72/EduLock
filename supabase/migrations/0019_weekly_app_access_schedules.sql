-- Optional per-app weekly allowed-hours schedule.
-- Empty JSON preserves existing default behaviour: allowed all day, every day.
alter table edulock.allowed_apps
  add column if not exists weekly_schedule jsonb not null default '{}'::jsonb;
alter table edulock.allowed_apps
  drop constraint if exists allowed_apps_weekly_schedule_object_check;
alter table edulock.allowed_apps
  add constraint allowed_apps_weekly_schedule_object_check
  check (jsonb_typeof(weekly_schedule) = 'object');
comment on column edulock.allowed_apps.weekly_schedule is
  'Optional weekday windows in child-device local time; keys mon..sun have enabled,start,end in HH:mm 24-hour format.';
grant select, insert, update, delete on edulock.allowed_apps to authenticated;
