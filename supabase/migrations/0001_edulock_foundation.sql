-- Isolated EduLock schema; does not modify public/HumanMesh tables.
create schema if not exists edulock;
revoke all on schema edulock from public;
grant usage on schema edulock to authenticated;
create table if not exists edulock.family_profiles (
 id uuid primary key default gen_random_uuid(), owner_user_id uuid not null references auth.users(id) on delete cascade,
 display_name text not null check (char_length(display_name) between 1 and 120), created_at timestamptz not null default now()
);
create table if not exists edulock.children (
 id uuid primary key default gen_random_uuid(), family_id uuid not null references edulock.family_profiles(id) on delete cascade,
 display_name text not null check (char_length(display_name) between 1 and 120), preferred_language text not null default 'en',
 created_at timestamptz not null default now()
);
create table if not exists edulock.subjects (
 id uuid primary key default gen_random_uuid(), family_id uuid not null references edulock.family_profiles(id) on delete cascade,
 title text not null check (char_length(title) between 1 and 160), language_code text not null default 'en', created_at timestamptz not null default now()
);
create table if not exists edulock.questions (
 id uuid primary key default gen_random_uuid(), subject_id uuid not null references edulock.subjects(id) on delete cascade,
 prompt text not null check (char_length(prompt) between 1 and 10000),
 question_type text not null check (question_type in ('multiple_choice','true_false','short_answer','numeric')),
 options jsonb not null default '[]'::jsonb, answer_key jsonb not null default '{}'::jsonb,
 image_path text, audio_path text, created_at timestamptz not null default now()
);
create table if not exists edulock.tasks (
 id uuid primary key default gen_random_uuid(), family_id uuid not null references edulock.family_profiles(id) on delete cascade,
 child_id uuid not null references edulock.children(id) on delete cascade, subject_id uuid references edulock.subjects(id) on delete set null,
 title text not null check (char_length(title) between 1 and 160), question_count integer not null check (question_count between 1 and 500),
 pass_percent integer not null default 70 check (pass_percent between 0 and 100),
 reward_minutes integer not null default 0 check (reward_minutes between 0 and 1440),
 daily_cap_minutes integer not null default 60 check (daily_cap_minutes between 0 and 1440),
 active boolean not null default true, created_at timestamptz not null default now()
);
create table if not exists edulock.attempts (
 id uuid primary key default gen_random_uuid(), task_id uuid not null references edulock.tasks(id) on delete cascade,
 child_id uuid not null references edulock.children(id) on delete cascade, answers jsonb not null default '[]'::jsonb,
 score_percent integer check (score_percent between 0 and 100), passed boolean not null default false,
 awarded_minutes integer not null default 0 check (awarded_minutes between 0 and 1440), completed_at timestamptz not null default now()
);
create table if not exists edulock.allowed_apps (
 id uuid primary key default gen_random_uuid(), child_id uuid not null references edulock.children(id) on delete cascade,
 package_name text not null check (char_length(package_name) between 3 and 255), display_name text not null,
 enabled boolean not null default true, unique(child_id, package_name)
);
create table if not exists edulock.screen_time_ledger (
 id uuid primary key default gen_random_uuid(), child_id uuid not null references edulock.children(id) on delete cascade,
 source_attempt_id uuid references edulock.attempts(id) on delete set null,
 minutes_delta integer not null check (minutes_delta between -1440 and 1440),
 reason text not null check (char_length(reason) between 1 and 300), created_at timestamptz not null default now()
);
create index if not exists family_profiles_owner_idx on edulock.family_profiles(owner_user_id);
create index if not exists children_family_idx on edulock.children(family_id);
create index if not exists subjects_family_idx on edulock.subjects(family_id);
create index if not exists tasks_child_active_idx on edulock.tasks(child_id, active);
create index if not exists attempts_child_time_idx on edulock.attempts(child_id, completed_at desc);
create index if not exists ledger_child_time_idx on edulock.screen_time_ledger(child_id, created_at desc);

create or replace function edulock.owns_family(target_family uuid) returns boolean language sql stable security definer set search_path = '' as $$
 select exists(select 1 from edulock.family_profiles f where f.id=target_family and f.owner_user_id=(select auth.uid()));
$$;
create or replace function edulock.owns_child(target_child uuid) returns boolean language sql stable security definer set search_path = '' as $$
 select exists(select 1 from edulock.children c join edulock.family_profiles f on f.id=c.family_id where c.id=target_child and f.owner_user_id=(select auth.uid()));
$$;
create or replace function edulock.owns_subject(target_subject uuid) returns boolean language sql stable security definer set search_path = '' as $$
 select exists(select 1 from edulock.subjects s join edulock.family_profiles f on f.id=s.family_id where s.id=target_subject and f.owner_user_id=(select auth.uid()));
$$;
create or replace function edulock.owns_task(target_task uuid) returns boolean language sql stable security definer set search_path = '' as $$
 select exists(select 1 from edulock.tasks t join edulock.family_profiles f on f.id=t.family_id where t.id=target_task and f.owner_user_id=(select auth.uid()));
$$;
revoke all on function edulock.owns_family(uuid) from public;
revoke all on function edulock.owns_child(uuid) from public;
revoke all on function edulock.owns_subject(uuid) from public;
revoke all on function edulock.owns_task(uuid) from public;
grant execute on function edulock.owns_family(uuid) to authenticated;
grant execute on function edulock.owns_child(uuid) to authenticated;
grant execute on function edulock.owns_subject(uuid) to authenticated;
grant execute on function edulock.owns_task(uuid) to authenticated;

alter table edulock.family_profiles enable row level security;
alter table edulock.children enable row level security;
alter table edulock.subjects enable row level security;
alter table edulock.questions enable row level security;
alter table edulock.tasks enable row level security;
alter table edulock.attempts enable row level security;
alter table edulock.allowed_apps enable row level security;
alter table edulock.screen_time_ledger enable row level security;
create policy family_owner_all on edulock.family_profiles for all to authenticated using(owner_user_id=(select auth.uid())) with check(owner_user_id=(select auth.uid()));
create policy children_owner_all on edulock.children for all to authenticated using(edulock.owns_family(family_id)) with check(edulock.owns_family(family_id));
create policy subjects_owner_all on edulock.subjects for all to authenticated using(edulock.owns_family(family_id)) with check(edulock.owns_family(family_id));
create policy questions_owner_all on edulock.questions for all to authenticated using(edulock.owns_subject(subject_id)) with check(edulock.owns_subject(subject_id));
create policy tasks_owner_all on edulock.tasks for all to authenticated using(edulock.owns_family(family_id)) with check(edulock.owns_family(family_id) and edulock.owns_child(child_id));
create policy attempts_owner_read on edulock.attempts for select to authenticated using(edulock.owns_child(child_id));
-- Production answer submission and reward issuance must be validated by an Edge Function.
create policy attempts_owner_insert on edulock.attempts for insert to authenticated with check(edulock.owns_child(child_id) and edulock.owns_task(task_id));
create policy apps_owner_all on edulock.allowed_apps for all to authenticated using(edulock.owns_child(child_id)) with check(edulock.owns_child(child_id));
create policy ledger_owner_read on edulock.screen_time_ledger for select to authenticated using(edulock.owns_child(child_id));
grant select, insert, update, delete on all tables in schema edulock to authenticated;
alter default privileges in schema edulock grant select, insert, update, delete on tables to authenticated;
