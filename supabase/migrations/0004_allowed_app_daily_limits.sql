-- Add per-app limits used by the current EduLock Android client.
-- Older databases created allowed_apps without this column; adding it is safe and preserves existing rows.
alter table edulock.allowed_apps
  add column if not exists daily_limit_minutes integer not null default 60
  check (daily_limit_minutes between 1 and 1440);
