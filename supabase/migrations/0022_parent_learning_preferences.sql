alter table edulock.family_profiles add column if not exists country_code text not null default 'PK';
alter table edulock.family_profiles add column if not exists learning_language_code text not null default 'en';
alter table edulock.family_profiles add column if not exists study_track text not null default 'none';
alter table edulock.family_profiles add column if not exists display_language_code text not null default 'en';
alter table edulock.family_profiles drop constraint if exists family_profiles_country_code_check;
alter table edulock.family_profiles add constraint family_profiles_country_code_check check (country_code ~ '^[A-Z]{2}$');
alter table edulock.family_profiles drop constraint if exists family_profiles_study_track_check;
alter table edulock.family_profiles add constraint family_profiles_study_track_check check (study_track in ('none','islamic','hindu','christian','sikh','buddhist','jewish','other'));
