-- Parent logout codes no longer expire by time. They remain single-use and are replaced by parent regeneration.
-- Revoke codes that were already expired before this migration to avoid reviving old stale codes.
update edulock.child_emergency_access
set logout_code_hash = '', logout_code_salt = '', logout_code_expires_at = null,
    logout_failed_attempts = 0, logout_locked_until = null, updated_at = now()
where logout_code_hash <> ''
  and logout_code_expires_at is not null
  and logout_code_expires_at <= now();

comment on column edulock.child_emergency_access.logout_code_expires_at is
  'Legacy compatibility column. New parent logout codes do not expire by time; codes are consumed once when used.';
