# EduLock

Online-first Android parental-control and learning app. Agreed scope: parent/child profiles, parent-authored multilingual questions, tasks and rewards, app-time limits, and progress reports.

## Stack
- Native Android: Kotlin, Jetpack Compose, Material 3
- Backend: Supabase only (Auth + PostgreSQL; Edge Functions for trusted operations)
- GitHub Actions builds and tests. Local cache is optional; Supabase remains the backend.

## Status
This is an initial foundation, not a finished production parental-control app. Authentication, question/task workflows, server-validated rewards, child-device pairing and app usage enforcement still need implementation and real-device tests. Ordinary Android apps cannot guarantee that they cannot be uninstalled or have their data cleared.

## Build
Open in Android Studio with JDK 17 and Android SDK, or run `gradle assembleDebug`. GitHub Actions uploads the debug APK as a workflow artifact.

## Supabase
1. Review and apply `supabase/migrations/0001_edulock_foundation.sql` in the intended existing project.
2. EduLock uses a separate `edulock` schema; this migration does not alter HumanMesh tables or auth configuration.
3. Add GitHub Actions secrets `SUPABASE_URL` and `SUPABASE_ANON_KEY` (public anon/publishable key only). Never embed a service-role key.
4. For local builds, configure the public endpoint and anon/publishable key in the app's build configuration before enabling client networking.
5. Reward issuance must be validated server-side before production.