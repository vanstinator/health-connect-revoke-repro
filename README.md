# Health Connect reads fail after revoking and granting permissions again

On a Pixel 10 Pro Fold running Android 17 QPR2 beta (`CP41.260831.016`), most Health Connect reads stop working after calling `revokeAllPermissions()` and granting permissions again.

This issue was not present on Android 17 QPR1 before I upgraded this device to the QPR2 beta series.

The only other device I have available at the moment is an older OnePlus 6 running Android 15 LineageOS. The issue is not present there, and revoking permissions before granting them again allows you to query Health Connect as expected.

To reproduce with the sample app:

1. Grant all Health Connect permissions.
2. Tap **Query Health Connect**. All reads succeed.
3. Tap **Revoke all Health Connect permissions** and confirm. The app closes to let the revocation take effect.
4. Reopen the app and grant all permissions again.
5. Tap **Query Health Connect**.

All reads should work again. Instead, steps, distance, and resting heart rate reads throw a `SecurityException` saying the required permission is missing. Heart rate reads still work.

The permissions show as granted in `getGrantedPermissions()`, `checkSelfPermission`, and `dumpsys package`. However, the UID-level `READ_WRITE_HEALTH_DATA` app op remains set to `ignore`:

```text
$ adb shell appops get dev.healthconnect.repro READ_WRITE_HEALTH_DATA
Uid mode: READ_WRITE_HEALTH_DATA: ignore
READ_WRITE_HEALTH_DATA: allow
```

This appears to explain the failures. Heart rate uses a separate app op, which would explain why it still works.

The sample uses Health Connect client `1.1.0`. All app code is in `app/src/main/java/dev/healthconnect/repro/MainActivity.kt`.