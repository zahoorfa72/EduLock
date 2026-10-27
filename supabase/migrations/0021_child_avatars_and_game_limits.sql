-- Parent-selected child avatars and daily educational mini-game allowance.
alter table edulock.children add column if not exists avatar_icon text not null default '🦊';
alter table edulock.children add column if not exists daily_game_limit_minutes integer not null default 20;
alter table edulock.children drop constraint if exists children_daily_game_limit_minutes_check;
alter table edulock.children add constraint children_daily_game_limit_minutes_check
  check (daily_game_limit_minutes between 0 and 1440);
alter table edulock.children drop constraint if exists children_avatar_icon_check;
alter table edulock.children add constraint children_avatar_icon_check
  check (char_length(avatar_icon) between 1 and 16);
comment on column edulock.children.avatar_icon is 'Parent-selected child profile emoji avatar.';
comment on column edulock.children.daily_game_limit_minutes is 'Daily in-app educational mini-game time in minutes; zero disables games.';
