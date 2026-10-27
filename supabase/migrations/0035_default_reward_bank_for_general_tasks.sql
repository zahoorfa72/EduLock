-- Make the Reward Bank the default route for newly created tasks and migrate
-- existing general-reward tasks so earned minutes are visible in the child's bank.
begin;

alter table edulock.tasks
  alter column reward_to_bank set default true;

update edulock.tasks
set reward_to_bank = true
where reward_package_name is null
  and reward_group_id is null
  and reward_to_bank = false;

notify pgrst, 'reload schema';
commit;
