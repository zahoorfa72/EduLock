# EduLock

Native Android parental-control and learning app built with Kotlin, Jetpack Compose and Supabase. Parents use email/password accounts; child devices use a private anonymous session linked by a parent's one-time pairing code. There is no child email/password signup or sign-in.

## Implemented in this iteration

- Parent account registration/sign-in through Supabase Auth; child devices pair without email or password using Supabase Anonymous Sign-Ins.
- Encrypted-at-rest session tokens using the Android Keystore; no saved passwords.
- Parent-owned family and child profiles with Row Level Security.
- One-time, six-digit child pairing codes; the server stores only a hash and expires codes after 10 minutes.
- Parent-created subjects and multilingual questions. Unicode input supports Urdu, Sindhi, English and other languages; Urdu/Sindhi screens use right-to-left layout.
- Concept Bridge: each canonical question has a reusable concept tag and language code; parents can save translated prompts/options and accepted answers. Children can switch to an available translation while answering, and the parent report groups correctness by concept and language without exposing translation answer keys.
- Task assignment, question answering, scores and reward ledger entries.
- Server-side answer grading. Correct-answer keys are not granted to clients. Rewards are written by a trusted database function, not calculated by the client, with a per-child daily cap and a task cap enforced in the same transaction.
- Progress reports with score, pass/fail and awarded minutes.
- Parent-facing daily usage reports for approved apps, showing package/display name and total foreground time only. Usage is synced from the child's device while EduLock is open; it is not a live screen view.
- Per-app daily usage limits are configurable from the parent account and applied locally when the child syncs device rules.
- Opt-in Android Accessibility service observes app/package changes only (not screen text), displays a transparent restriction overlay over unapproved apps and blocks approved apps when their allowed time is used up. This requires a parent-approved one-time activation on the child's physical device.
- If the phone was provisioned as an Android device owner, EduLock applies Android policies to block app uninstall and app-management actions (including clearing app data through Settings). The app cannot grant itself device-owner status.
- Shared rounded, high-contrast kid-friendly styling across app screens, with a colorful gradient header and clearer status cards.

## Important limitations

A bug-free claim cannot be made without successful CI, database deployment and tests on the actual target devices. On an ordinary consumer device, EduLock cannot guarantee protection against uninstall or clearing app data. Device-admin mode may add uninstall friction only. Stronger Android-enforced protection is applied only when EduLock is provisioned as the device owner; the app then blocks uninstall and app-management/Clear data actions exposed through Settings. Android recovery, a factory reset, ADB or OEM-specific paths may still bypass controls, so test the intended device model. Accessibility overlays are a separate, user-enabled app-blocking feature.

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
   - `supabase/migrations/0004_per_app_daily_limits.sql`
   - `supabase/migrations/0005_app_usage_reporting.sql`
3. Deploy these functions from the repository using the Supabase CLI linked to the intended project:
   - `supabase functions deploy create-child-pairing-code`
   - `supabase functions deploy claim-child-pairing`
   - `supabase functions deploy submit-task-attempt`
4. Confirm the Edge Function environment has Supabase's server-provided `SUPABASE_URL`, `SUPABASE_ANON_KEY`, and `SUPABASE_SERVICE_ROLE_KEY`. The service-role key is used only inside Edge Functions; it must never be copied into Android, GitHub Actions build variables, or chat.
5. In Supabase Auth, choose the appropriate email-confirmation policy. When confirmation is required, a user must confirm the email and then sign in before linking a child account.
6. In Supabase Auth settings, enable **Anonymous Sign-Ins** for the EduLock project. Create a parent account in EduLock, add a child profile and generate a pairing code. On the child's device, choose **Set up a child's device with pairing code** and enter the six-digit code. No child email/password account is created. Codes expire after ten minutes and work once.
7. Create a subject, add questions, assign a task, and test scoring/caps using a non-production test account before using family data.

### Stronger uninstall and Clear data protection (managed devices only)

For Android-enforced protection, provision the child's phone as a fully managed/device-owner device **before normal use**. This usually requires a fresh/reset device and managed-device enrollment; ordinary device-admin activation inside the app is not sufficient. With USB debugging and Android platform tools available, a test device can be provisioned after installing EduLock with:

```sh
adb shell dpm set-device-owner com.edulock.app/.EduLockDeviceAdminReceiver
```

This command can fail if the device does not meet Android's provisioning requirements. Do not run it on a device where you cannot accept the setup/reset implications. When launched as device owner, EduLock applies `setUninstallBlocked`, `DISALLOW_APPS_CONTROL` and `DISALLOW_UNINSTALL_APPS`; the child dashboard reports the stronger status only when these policies are confirmed active. Clear data via Settings is restricted in managed mode, but no app should claim absolute protection against all recovery, reset or debugging paths.

### Edge Function security notes

- Pair-code generation requires an authenticated parent. Pair-code claiming uses a child anonymous session created by Supabase Auth; the server checks the stored child role, enforces attempt limits, and consumes the one-time code atomically.
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
