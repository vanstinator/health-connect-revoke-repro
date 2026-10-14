package dev.healthconnect.repro

import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.system.exitProcess

private const val TAG = "HC_PERMISSION_PROBE"

private val REQUESTED_PERMISSIONS =
    setOf(
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
    )

class MainActivity : ComponentActivity() {
    private lateinit var output: TextView
    private val client by lazy { HealthConnectClient.getOrCreate(applicationContext) }

    @Suppress("UNCHECKED_CAST")
    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
            as ActivityResultContract<Set<String>, Set<String>>
    ) { granted ->
        line("Granted ${granted.size} of ${REQUESTED_PERMISSIONS.size} requested permissions")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        output = TextView(this).apply {
            setTextIsSelectable(true)
            textSize = 13f
            setPadding(24, 24, 24, 24)
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(button("Request Health Connect permissions") {
                permissionLauncher.launch(REQUESTED_PERMISSIONS)
            })
            addView(button("Query Health Connect") { query() })
            addView(button("Revoke all Health Connect permissions") { revoke() })
        }

        val scroll = ScrollView(this).apply {
            addView(
                output,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(buttons)
            addView(
                scroll,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val safeArea = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(safeArea.left, safeArea.top, safeArea.right, safeArea.bottom)
            windowInsets
        }
        setContentView(root)
        ViewCompat.requestApplyInsets(root)
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    private fun query() {
        output.text = ""
        lifecycleScope.launch {
            line("Android ${Build.VERSION.SDK_INT}, build ${Build.DISPLAY}")
            val granted = client.permissionController.getGrantedPermissions()
            line(
                "Granted ${REQUESTED_PERMISSIONS.count { it in granted }} of " +
                    "${REQUESTED_PERMISSIONS.size} requested permissions"
            )

            val end = Instant.now()
            val range = TimeRangeFilter.between(end.minus(31, ChronoUnit.DAYS), end)
            val results = listOf(
                read("RestingHeartRate") {
                    client.readRecords(ReadRecordsRequest<RestingHeartRateRecord>(range)).records.size
                },
                read("Steps") {
                    client.readRecords(ReadRecordsRequest<StepsRecord>(range)).records.size
                },
                read("Distance") {
                    client.readRecords(ReadRecordsRequest<DistanceRecord>(range)).records.size
                },
                read("HeartRate") {
                    client.readRecords(ReadRecordsRequest<HeartRateRecord>(range)).records.size
                }
            )

            if (results.any { !it }) {
                line("")
                line("Reads failed. Check the app op with:")
                line("adb shell appops get $packageName READ_WRITE_HEALTH_DATA")
            }
        }
    }

    private suspend fun read(name: String, count: suspend () -> Int): Boolean =
        try {
            line("PASS $name: ${count()} records")
            true
        } catch (exception: Exception) {
            line("FAIL $name: ${exception.stackTraceToString()}")
            false
        }

    private fun revoke() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    client.permissionController.revokeAllPermissions()
                }
            } catch (exception: Exception) {
                line("FAIL revokeAllPermissions: ${exception.stackTraceToString()}")
                return@launch
            }
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Revoking permissions")
                .setMessage(
                    "Android revokes the permissions once the app is closed. " +
                        "The app will now close."
                )
                .setCancelable(false)
                .setPositiveButton("OK") { _, _ -> closeApp() }
                .show()
        }
    }

    // Context.revokeSelfPermissionsOnKill only revokes once the app's processes are gone.
    // Exiting after a delay lets the finished task leave the screen first, so Android
    // does not restart the activity.
    private fun closeApp() {
        finishAndRemoveTask()
        Handler(Looper.getMainLooper()).postDelayed({ exitProcess(0) }, 500)
    }

    private fun line(message: String) {
        Log.i(TAG, message)
        runOnUiThread {
            output.append(message)
            output.append("\n")
        }
    }
}
