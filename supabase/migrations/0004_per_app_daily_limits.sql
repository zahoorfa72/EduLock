-- Per-app daily usage limits for EduLock. Additive and scoped only to the edulock schema.
alter table edulock.allowed_apps
  add column if not exists daily_limit_minutes integer not null default 60
  check (daily_limit_minutes between 1 and 1440);
