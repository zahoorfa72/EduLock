-- Religion/study track belongs to each child profile, not the parent's sign-up form.
-- Country only changes the order of suggestions; the parent makes the actual choice.
alter table edulock.children
  add column if not exists study_track text not null default 'none';

alter table edulock.children
  drop constraint if exists children_study_track_check;

alter table edulock.children
  add constraint children_study_track_check
  check (study_track in ('none','islamic','hindu','christian','sikh','buddhist','jewish','jain','other'));

comment on column edulock.children.study_track is
  'Optional parent-selected religious education track for this child; not inferred from country.';
