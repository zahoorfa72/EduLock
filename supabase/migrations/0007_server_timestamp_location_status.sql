-- Use database server time for check-in/updated timestamps so device clock drift
-- cannot create false offline alerts. Preserve recorded_at as the GPS fix's source time.
create or replace function edulock.set_child_device_server_timestamps()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.last_seen_at := now();
  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists child_device_status_server_timestamps on edulock.child_device_status;
create trigger child_device_status_server_timestamps
before insert or update on edulock.child_device_status
for each row execute function edulock.set_child_device_server_timestamps();

create or replace function edulock.set_child_location_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists child_location_updated_at on edulock.child_locations;
create trigger child_location_updated_at
before insert or update on edulock.child_locations
for each row execute function edulock.set_child_location_updated_at();

revoke all on function edulock.set_child_device_server_timestamps() from public;
revoke all on function edulock.set_child_location_updated_at() from public;
