-- Device heartbeat records for transparent parent-authorized location sharing.
-- A missing heartbeat means "not reporting"; it cannot prove the phone is powered off.
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
create policy child_device_status_parent_read on edulock.child_device_status
  for select to authenticated
  using (edulock.is_parent() and edulock.owns_child(child_id));

drop policy if exists child_device_status_child_insert on edulock.child_device_status;
create policy child_device_status_child_insert on edulock.child_device_status
  for insert to authenticated
  with check (exists (
    select 1 from edulock.children c
    where c.id = child_device_status.child_id and c.child_user_id = (select auth.uid())
  ));

drop policy if exists child_device_status_child_update on edulock.child_device_status;
create policy child_device_status_child_update on edulock.child_device_status
  for update to authenticated
  using (exists (
    select 1 from edulock.children c
    where c.id = child_device_status.child_id and c.child_user_id = (select auth.uid())
  ))
  with check (exists (
    select 1 from edulock.children c
    where c.id = child_device_status.child_id and c.child_user_id = (select auth.uid())
  ));
