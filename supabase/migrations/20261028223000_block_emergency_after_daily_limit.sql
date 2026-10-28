-- Parents may opt out of emergency overrides after an approved app has used its daily limit.
alter table edulock.child_emergency_access
  add column if not exists block_emergency_after_daily_limit boolean not null default false;

comment on column edulock.child_emergency_access.block_emergency_after_daily_limit is
  'When true, emergency access cannot extend an enabled app beyond its configured daily usage limit.';
