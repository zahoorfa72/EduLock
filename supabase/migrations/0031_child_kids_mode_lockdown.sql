-- Parent-controlled strict app allowlist for EduLock Kids Mode.
-- OFF preserves current per-app behavior; existing profiles are not unexpectedly locked.
alter table edulock.children
    add column if not exists kids_mode_enabled boolean not null default false;

comment on column edulock.children.kids_mode_enabled is
    'When true, child devices block launchable apps not explicitly enabled in edulock.allowed_apps. Accessibility is the ordinary-device fallback; device-owner provisioning is required for managed kiosk enforcement.';
