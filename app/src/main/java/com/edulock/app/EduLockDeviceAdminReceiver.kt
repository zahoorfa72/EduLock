package com.edulock.app

import android.app.admin.DeviceAdminReceiver

/**
 * Visible, opt-in device-admin enrollment for ordinary uninstall friction.
 * This receiver does not collect data or perform remote device actions.
 */
class EduLockDeviceAdminReceiver : DeviceAdminReceiver()
