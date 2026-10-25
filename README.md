# EduLock

Native Android parental-control and learning app built with Kotlin, Jetpack Compose and Supabase. Parent and child access are separate authenticated roles; a child does not gain parent access simply by switching a screen.

## Implemented in this iteration

- Parent and child account registration/sign-in through Supabase Auth.
- Encrypted-at-rest session tokens using the Android Keystore; no saved passwords.
- Parent-owned family and child profiles with Row Level Security.
- One-time, six-digit child pairing codes; the server stores only a hash and expires codes after 10 minutes.
- Parent-created subjects and multilingual questions. Unicode input supports Urdu, Sindhi, English and other languages; Urdu/Sindhi screens use right-to-left layout.
- Concept Bridge: each canonical question has a reusable concept tag and language code; parents can save translated prompts/options and accepted answers. Children can switch to an available translation while answering, and the parent report groups correctness by concept and language without exposing translation answer keys.
- Task assignment, question answering, scores and reward ledger entries.
- Server-side answer grading. Correct-answer keys are not granted to clients. Rewards are written by a trusted database function, not calculated by the client, with a per-child daily cap and a task cap enforced in the same transaction.
- Progress reports with score, pass/fail and awarded minutes.
- Opt-in Android Accessibility service that observes app/package changes only (not screen text), blocks unapproved apps and blocks approved apps when earned time is used up. This requires a parent-approved one-time activation on the child's physical device.

## Important limitations

A bug-free claim cannot be made without successful CI, database deployment and tests on the actual target devices. Android restrictions can be disabled or bypassed on ordinary consumer devices, and an ordinary app cannot guarantee that it cannot be uninstalled or have its data cleared. The Accessibility service is a supported, user-enabled restriction mechanism—not device-owner / MDM enforcement. Stronger control requires provisioning the child device as a managed/device-owner device and testing OEM-specific behavior.

This code does not include an APK signing/release configuration, a deployed Supabase project, nor proof that the current device has granted Accessibility access. The GitHub Actions build is the first build check; run the test steps below before public launch.

## Android build

- Android Studio with JDK 17 and Android SDK 35, or the GitHub Actions workflow.
- Set repository Actions secrets:
  - `SUPABASE_URL`: the HTTPS URL of the EduLock Supabase project.
  - `SUPABASE_ANON_KEY`: the public anon/publishable key only. Never put a service-role key in the Android build.
- The workflow passes those values into Gradle BuildConfig fields. If missing, the app intentionally shows a configuration message rather than pretending sync is active.
- Build locally with `gradle assembleDebug`; GitHub Actions uploads `app-debug.apk` as the `EduLock-debug-apk` artifact.

## Supabase setup

Use the intended EduLock Supabase project. EduLock uses its own `edulock` schema and does not change HumanMesh's `public` tables, but do not run these migrations in an unintended project.

1. In Supabase Dashboard → **Project Settings → API → Exposed schemas**, add `edulock` while preserving all existing exposed schemas. PostgREST needs to expose the custom schema for this app and the Edge Functions.
2. Apply migrations in numerical order:
   - `supabase/migrations/0001_edulock_foundation.sql`
   - `supabase/migrations/0002_secure_parent_child_rewards.sql`
   - `supabase/migrations/0003_concept_bridge_translations.sql`
3. Deploy these functions from the repository using the Supabase CLI linked to the intended project:
   - `supabase functions deploy create-child-pairing-code`
   - `supabase functions deploy claim-child-pairing`
   - `supabase functions deploy submit-task-attempt`
4. Confirm the Edge Function environment has Supabase's server-provided `SUPABASE_URL`, `SUPABASE_ANON_KEY`, and `SUPABASE_SERVICE_ROLE_KEY`. The service-role key is used only inside Edge Functions; it must never be copied into Android, GitHub Actions build variables, or chat.
5. In Supabase Auth, choose the appropriate email-confirmation policy. When confirmation is required, a user must confirm the email and then sign in before linking a child account.
6. Create a parent account in the app. Add a child profile, generate a pairing code, then on the child's device register/sign in using **Child** account mode and enter the code. Codes are one-time and expire after ten minutes.
7. Create a subject, add questions, assign a task, and test scoring/caps using a non-production test account before using family data.

### Edge Function security notes

- Pairing endpoints require a valid signed-in user and verify parent/child roles.
- Pairing codes are not persisted as plaintext and the claim operation is atomic.
- Task grading checks that the signed-in child owns the task, re-reads answer keys on the server, checks every question ID, serializes submissions per child and writes the attempt and ledger record transactionally.
- API clients cannot select the `answer_key` column. Do not grant direct client inserts into `attempts` or `screen_time_ledger`.
- Never expose `SUPABASE_SERVICE_ROLE_KEY`; it bypasses Row Level Security.

## Test checklist before release

- [ ] GitHub Actions Android build passes and the APK installs on Android 8.0+.
- [ ] Register parent, confirm email if required, sign in, close/reopen, and verify session refresh.
- [ ] Create two families and verify neither parent can read the other's children, subjects, tasks or reports.
- [ ] Create a child account and use a valid pairing code; test expired, reused, wrong-family and parent-account codes.
- [ ] Verify Urdu, Sindhi (RTL), English and another Unicode language render and save correctly.
- [ ] Create a concept-tagged question, add two translations, verify child language switching keeps the same question identity, and confirm parent reports show correct/total separately by language.
- [ ] Confirm child API requests cannot select translation accepted_answers or directly write concept progress rows.
- [ ] Test all question types, empty submissions, changed question IDs, repeat submissions, fail/pass boundaries and concurrent reward requests.
- [ ] Verify child clients cannot read answer keys or directly insert attempts/reward ledger rows.
- [ ] Test daily cap at zero, exactly at the cap, and across UTC day boundaries.
- [ ] On each target device/OEM, enable Accessibility, test unapproved-app blocking, allowed-app time accounting, restart/reboot, permission revocation and the recovery path in Android Settings.
- [ ] Review Google Play Accessibility API policy and parental-control disclosure requirements before public release.

## Project structure

- `app/`: native Android app, encrypted session storage, Supabase REST/Auth client and Accessibility service.
- `supabase/migrations/`: isolated schema, RLS policies and server-side reward operations.
- `supabase/functions/`: protected pairing and task-submission endpoints.
- `.github/workflows/android.yml`: debug APK build.
