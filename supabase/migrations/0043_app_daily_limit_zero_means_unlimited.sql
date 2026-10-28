begin;
alter table edulock.allowed_apps alter column daily_limit_minutes set default 0;
alter table edulock.allowed_apps drop constraint if exists allowed_apps_daily_limit_minutes_check;
alter table edulock.allowed_apps add constraint allowed_apps_daily_limit_minutes_check
  check (daily_limit_minutes between 0 and 1440);

-- 1440 was the old representation of "unrestricted"; migrate it to 0 while
-- preserving any genuinely configured lower limit.
update edulock.allowed_apps set daily_limit_minutes = 0 where daily_limit_minutes = 1440;

create or replace function edulock.default_allow_discovered_app()
returns trigger language plpgsql security definer set search_path = ''
as $$
begin
  insert into edulock.allowed_apps(child_id, package_name, display_name, enabled, daily_limit_minutes)
  values (new.child_id, new.package_name, new.display_name, true, 0)
  on conflict (child_id, package_name) do update set display_name = excluded.display_name;
  return new;
end;
$$;

insert into edulock.allowed_apps(child_id, package_name, display_name, enabled, daily_limit_minutes)
select i.child_id, i.package_name, i.display_name, true, 0
from edulock.child_installed_apps i
on conflict (child_id, package_name) do update set display_name = excluded.display_name;
commit;