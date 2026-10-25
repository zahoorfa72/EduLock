EduLock uses an isolated `edulock` schema and does not modify existing public/HumanMesh tables. Apply migrations manually after review. Configure PostgREST exposed schemas to include `edulock` if querying via the Supabase client. Android must use only the anon/publishable key, never service-role key.

Before production, validate answer correctness and award time in a Supabase Edge Function; do not let child clients mint trusted reward balances. Test RLS with separate users before launch.
