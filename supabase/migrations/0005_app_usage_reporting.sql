-- Daily per-app usage summaries for the linked child device.
-- Stores package-level totals only; no screen content, keystrokes, messages, or browser history.
create table if not exists edulock.app_usage_daily (
  id uuid primary key default gen_random_uuid(),
  child_id uuid not null references edulock.children(id) on delete cascade,
  usage_date date not null,
  package_name text not null check (char_length(package_name) between 3 and 255),
  display_name text not null check (char_length(display_name) between 1 and 120),
  used_seconds bigint not null default 0 check (used_seconds >= 0 and used_seconds <= 86400),
  updated_at timestamptz not null default now(),
  unique (child_id, usage_date, package_name)
);
create index if not exists app_usage_daily_child_date_idx
  on edulock.app_usage_daily(child_id, usage_date desc);
alter table edulock.app_usage_daily enable row level security;
revoke all on edulock.app_usage_daily from public, anon;
grant select, insert, update on edulock.app_usage_daily to authenticated;

drop policy if exists app_usage_child_own_write on edulock.app_usage_daily;
create policy app_usage_child_own_write on edulock.app_usage_daily
  for all to authenticated
  using (
    exists (
      select 1 from edulock.children c
      where c.id = app_usage_daily.child_id
        and c.child_user_id = (select auth.uid())
    )
  )
  with check (
    exists (
      select 1 from edulock.children c
      where c.id = app_usage_daily.child_id
        and c.child_user_id = (select auth.uid())
    )
  );

drop policy if exists app_usage_parent_read on edulock.app_usage_daily;
create policy app_usage_parent_read on edulock.app_usage_daily
  for select to authenticated
  using (
    edulock.is_parent() and exists (
      select 1
      from edulock.children c
      join edulock.family_profiles f on f.id = c.family_id
      where c.id = app_usage_daily.child_id
        and f.owner_user_id = (select auth.uid())
    )
  );


-- Latest location only; transparent sharing is enabled by the child device.
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
create policy child_locations_parent_read on edulock.child_locations
  for select to authenticated using (edulock.is_parent() and edulock.owns_child(child_id));

drop policy if exists child_locations_child_write on edulock.child_locations;
create policy child_locations_child_write on edulock.child_locations
  for insert to authenticated with check (exists (
    select 1 from edulock.children c
    where c.id = child_locations.child_id and c.child_user_id = (select auth.uid())
  ));

drop policy if exists child_locations_child_update on edulock.child_locations;
create policy child_locations_child_update on edulock.child_locations
  for update to authenticated using (exists (
    select 1 from edulock.children c
    where c.id = child_locations.child_id and c.child_user_id = (select auth.uid())
  )) with check (exists (
    select 1 from edulock.children c
    where c.id = child_locations.child_id and c.child_user_id = (select auth.uid())
  ));
