alter table edulock.family_profiles
  drop constraint if exists family_profiles_study_track_check;
alter table edulock.family_profiles
  add constraint family_profiles_study_track_check
  check (study_track in ('none','islamic','hindu','christian','sikh','buddhist','jewish','jain','other'));
