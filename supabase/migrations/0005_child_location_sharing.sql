-- Transparent, parent-authorized child location sharing.
-- Store only the latest reported point per paired child; do not create a location history trail.
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
 for select to authenticated using (edulock.owns_child(child_id) and edulock.is_parent());
drop policy if exists child_locations_child_write on edulock.child_locations;
create policy child_locations_child_write on edulock.child_locations
 for insert to authenticated with check (
   exists(select 1 from edulock.children c where c.id = child_locations.child_id and c.child_user_id = (select auth.uid()))
 );
drop policy if exists child_locations_child_update on edulock.child_locations;
create policy child_locations_child_update on edulock.child_locations
 for update to authenticated using (
   exists(select 1 from edulock.children c where c.id = child_locations.child_id and c.child_user_id = (select auth.uid()))
 ) with check (
   exists(select 1 from edulock.children c where c.id = child_locations.child_id and c.child_user_id = (select auth.uid()))
 );
