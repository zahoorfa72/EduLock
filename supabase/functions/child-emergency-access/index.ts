import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });

async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest)).map((b) => b.toString(16).padStart(2, "0")).join("");
}
function safeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}
function karachiDate(date = new Date()): string {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Karachi", year: "numeric", month: "2-digit", day: "2-digit",
  }).formatToParts(date);
  const part = (type: string) => parts.find((p) => p.type === type)?.value ?? "";
  return part("year") + "-" + part("month") + "-" + part("day");
}
function isFuture(value: unknown): boolean {
  return typeof value === "string" && Number.isFinite(Date.parse(value)) && Date.parse(value) > Date.now();
}
async function recordFailure(admin: ReturnType<typeof createClient>, childId: string, kind: "emergency" | "logout", previous: number) {
  const attempts = Math.min(100, previous + 1);
  const prefix = kind === "emergency" ? "emergency" : "logout";
  const update: Record<string, unknown> = {
    [prefix + "_failed_attempts"]: attempts,
    [prefix + "_locked_until"]: attempts >= 5 ? new Date(Date.now() + 15 * 60_000).toISOString() : null,
    updated_at: new Date().toISOString(),
  };
  await admin.schema("edulock").from("child_emergency_access").update(update).eq("child_id", childId);
}
Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !anonKey || !serviceKey) return json({ error: "Server configuration is incomplete" }, 500);
  const authorization = req.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) return json({ error: "Sign in on the child device first" }, 401);

  try {
    const caller = createClient(url, anonKey, {
      global: { headers: { Authorization: authorization } },
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data: authData, error: authError } = await caller.auth.getUser();
    if (authError || !authData.user) return json({ error: "Session expired; sign in again" }, 401);

    const admin = createClient(url, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });
    const { data: role, error: roleError } = await admin.schema("edulock")
      .from("user_profiles").select("account_role").eq("user_id", authData.user.id).maybeSingle();
    if (roleError || role?.account_role !== "child") return json({ error: "This action is available only from a linked child profile" }, 403);

    const { data: child, error: childError } = await admin.schema("edulock")
      .from("children").select("id,family_id,child_user_id").eq("child_user_id", authData.user.id).maybeSingle();
    if (childError || !child) return json({ error: "This device is not linked to a child profile" }, 403);

    const body = await req.json().catch(() => ({}));
    const action = typeof body.action === "string" ? body.action : "";
    const { data: settings, error: settingsError } = await admin.schema("edulock")
      .from("child_emergency_access").select("*").eq("child_id", child.id).eq("family_id", child.family_id).maybeSingle();
    if (settingsError) {
      console.error("Emergency access lookup failed", settingsError.code);
      return json({ error: "Emergency access is not available yet. Update EduLock and retry." }, 503);
    }
    if (!settings) return json({ error: "Your parent has not configured emergency access for this profile." }, 403);

    if (action === "options") {
      const available = settings.enabled === true &&
        isFuture(settings.expires_at) &&
        typeof settings.emergency_code_hash === "string" &&
        settings.emergency_code_hash.length > 0;
      return json({
        enabled: available,
        allowed_packages: available && Array.isArray(settings.allowed_packages) ? settings.allowed_packages : [],
        access_minutes: Math.max(1, Math.min(30, Number(settings.access_minutes) || 10)),
        expires_at: available ? settings.expires_at : null,
        last_used_date: settings.emergency_last_used_date ?? null,
      });
    }

    const code = typeof body.code === "string" ? body.code.replace(/\D/g, "") : "";
    if (!/^\d{8}$/.test(code)) return json({ error: "Enter the eight-digit code created by the parent" }, 400);

    if (action === "logout") {
      if (isFuture(settings.logout_locked_until)) return json({ error: "Too many incorrect codes. Try again after 15 minutes." }, 429);
      const valid = isFuture(settings.logout_code_expires_at) &&
        typeof settings.logout_code_hash === "string" && settings.logout_code_hash.length > 0 &&
        safeEqual(await sha256(String(settings.logout_code_salt) + ":" + code), settings.logout_code_hash);
      if (!valid) {
        await recordFailure(admin, child.id, "logout", Number(settings.logout_failed_attempts) || 0);
        return json({ error: "The parent code is incorrect or expired. Ask the parent to generate a new one." }, 403);
      }
      // Conditional update consumes a code exactly once, including concurrent requests.
      const { data: consumed, error: consumeError } = await admin.schema("edulock")
        .from("child_emergency_access")
        .update({
          logout_code_hash: "", logout_code_salt: "", logout_code_expires_at: null,
          logout_failed_attempts: 0, logout_locked_until: null, updated_at: new Date().toISOString(),
        })
        .eq("child_id", child.id).eq("logout_code_hash", settings.logout_code_hash)
        .select("child_id").maybeSingle();
      if (consumeError || !consumed) return json({ error: "This code was already used. Ask the parent for a fresh code." }, 409);
      return json({ authorized: true, action: "logout" });
    }

    if (action !== "emergency") return json({ error: "Unknown action" }, 400);
    if (settings.enabled !== true || !isFuture(settings.expires_at) ||
        !settings.emergency_code_hash || !Array.isArray(settings.allowed_packages)) {
      return json({ error: "Emergency access is turned off or its code has expired." }, 403);
    }
    if (isFuture(settings.emergency_locked_until)) return json({ error: "Too many incorrect codes. Try again after 15 minutes." }, 429);

    const packageName = typeof body.package_name === "string" ? body.package_name.trim() : "";
    if (!/^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$/.test(packageName)) {
      return json({ error: "Choose a valid app" }, 400);
    }
    const codeValid = safeEqual(await sha256(String(settings.emergency_code_salt) + ":" + code), String(settings.emergency_code_hash));
    const packageAllowed = (settings.allowed_packages as unknown[]).includes(packageName);
    if (!codeValid || !packageAllowed) {
      await recordFailure(admin, child.id, "emergency", Number(settings.emergency_failed_attempts) || 0);
      return json({ error: "The code is incorrect or this app was not approved for emergency access." }, 403);
    }

    const today = karachiDate();
    if (settings.emergency_last_used_date === today) {
      return json({ error: "Emergency access has already been used today. It resets after midnight Pakistan time." }, 429);
    }
    // Approval is limited to apps that are still enabled by the parent at redemption time.
    const { data: appRule, error: appError } = await admin.schema("edulock").from("allowed_apps")
      .select("package_name,enabled").eq("child_id", child.id).eq("package_name", packageName).eq("enabled", true).maybeSingle();
    if (appError || !appRule) return json({ error: "The parent has not currently enabled this app." }, 403);

    const duration = Math.max(1, Math.min(30, Number(settings.access_minutes) || 10));
    const { data: used, error: useError } = await admin.schema("edulock").from("child_emergency_access")
      .update({
        emergency_last_used_date: today,
        emergency_last_used_at: new Date().toISOString(),
        emergency_failed_attempts: 0,
        emergency_locked_until: null,
        updated_at: new Date().toISOString(),
      })
      .eq("child_id", child.id)
      .eq("enabled", true)
      .eq("emergency_code_hash", settings.emergency_code_hash)
      .or("emergency_last_used_date.is.null,emergency_last_used_date.lt." + today)
      .select("child_id")
      .maybeSingle();
    if (useError || !used) return json({ error: "Daily emergency access is already used or the settings changed. Try again tomorrow." }, 409);

    return json({
      authorized: true,
      package_name: packageName,
      access_minutes: duration,
      expires_in_seconds: duration * 60,
      used_date: today,
    });
  } catch (error) {
    console.error("child-emergency-access failed", error);
    return json({ error: "Could not verify the parent authorization. Check internet and retry." }, 500);
  }
});
