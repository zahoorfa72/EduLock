-- EduLock child profile deletion and private worked solutions.
-- Versioned after the existing 0010 repair migration.
create schema if not exists edulock;
grant usage on schema edulock to authenticated;

create table if not exists edulock.question_solutions (
  id uuid primary key default gen_random_uuid(),
  question_id uuid not null unique references edulock.questions(id) on delete cascade,
  solution text not null check (char_length(solution) between 1 and 12000),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists question_solutions_question_idx
  on edulock.question_solutions(question_id);

alter table edulock.question_solutions enable row level security;
revoke all on edulock.question_solutions from public, anon;
grant select, insert, update, delete on edulock.question_solutions to authenticated;
grant select, insert, update, delete on edulock.question_solutions to service_role;

drop policy if exists question_solutions_parent_manage on edulock.question_solutions;
create policy question_solutions_parent_manage
  on edulock.question_solutions for all to authenticated
  using (
    edulock.is_parent()
    and exists (
      select 1 from edulock.questions q
      where q.id = question_solutions.question_id
        and edulock.owns_subject(q.subject_id)
    )
  )
  with check (
    edulock.is_parent()
    and exists (
      select 1 from edulock.questions q
      where q.id = question_solutions.question_id
        and edulock.owns_subject(q.subject_id)
    )
  );

-- Delete only a profile that belongs to the authenticated parent. FK cascades clean
-- tasks, attempts, rewards, app rules, app inventory, usage summaries and child status rows.
create or replace function edulock.delete_child_profile(p_child_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $delete_child$
declare
  v_deleted integer;
begin
  if not exists (
    select 1 from edulock.user_profiles p
    where p.user_id = (select auth.uid()) and p.account_role = 'parent'
  ) then
    raise exception 'Parent access is required';
  end if;

  delete from edulock.children c
  using edulock.family_profiles f
  where c.id = p_child_id
    and c.family_id = f.id
    and f.owner_user_id = (select auth.uid());

  get diagnostics v_deleted = row_count;
  if v_deleted <> 1 then
    raise exception 'Child profile not found or access denied';
  end if;

  return jsonb_build_object('deleted', true, 'child_id', p_child_id);
end;
$delete_child$;

revoke all on function edulock.delete_child_profile(uuid) from public, anon;
grant execute on function edulock.delete_child_profile(uuid) to authenticated;

notify pgrst, 'reload schema';
