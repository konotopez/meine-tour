package com.artem.medtracker

import android.content.ActivityNotFoundException
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Environment
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.content.FileProvider
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import org.json.JSONObject
import java.io.File
import java.time.Instant

class AndroidBridge(
    private val activity: MainActivity,
    private val webView: WebView,
) {
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(activity) }
    @Volatile
    private var lastLocationJson: String? = null

    @JavascriptInterface
    fun requestLocationRefresh(reason: String?) {
        activity.runOnUiThread {
            if (!activity.hasAnyLocationPermission()) {
                activity.requestLocationPermissions()
            } else {
                refreshLocation(reason ?: "manual")
            }
        }
    }

    @JavascriptInterface
    fun getLastLocationJson(): String = lastLocationJson ?: "{}"

    @JavascriptInterface
    fun openMap(address: String?) {
        activity.runOnUiThread {
            val query = Uri.encode(address?.ifBlank { "Hamburg" } ?: "Hamburg")
            val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$query"))
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$query"))
            if (!tryStartActivity(geoIntent) && !tryStartActivity(webIntent)) {
                activity.toast(activity.getString(R.string.open_in_maps_failed))
            }
        }
    }

    @JavascriptInterface
    fun dialPhone(number: String?) {
        activity.runOnUiThread {
            val sanitized = number?.trim().orEmpty()
            if (sanitized.isBlank()) return@runOnUiThread
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(sanitized)}"))
            if (!tryStartActivity(intent)) {
                activity.toast(activity.getString(R.string.open_dialer_failed))
            }
        }
    }

    @JavascriptInterface
    fun saveHtmlReport(filename: String, htmlContent: String) {
        activity.runOnUiThread {
            runCatching {
                val file = File(reportsDirectory(), sanitizeFileName(filename.ifBlank { "tabelle.html" }))
                file.writeText(htmlContent, Charsets.UTF_8)
                shareFile(file, "text/html", activity.getString(R.string.share_html_chooser))
                activity.toast(activity.getString(R.string.saved_html_message))
            }.onFailure {
                activity.toast(activity.getString(R.string.save_failed))
            }
        }
    }

    @JavascriptInterface
    fun generatePdfReport(payloadJson: String) {
        activity.runOnUiThread {
            runCatching {
                val payload = parseReportPayload(payloadJson)
                val file = ReportPdfGenerator.generate(activity, payload)
                shareFile(file, "application/pdf", activity.getString(R.string.share_pdf_chooser))
                activity.toast(activity.getString(R.string.saved_pdf_message))
            }.onFailure {
                activity.toast(activity.getString(R.string.save_failed))
            }
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        if (granted) {
            refreshLocation("permission-result")
        } else {
            dispatchLocationError(activity.getString(R.string.location_permission_denied))
        }
    }

    fun refreshLocation(reason: String = "manual") {
        if (!activity.hasAnyLocationPermission()) {
            dispatchLocationError(activity.getString(R.string.location_permission_denied))
            return
        }

        val cancellationTokenSource = CancellationTokenSource()
        locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    publishLocation(location, reason)
                } else {
                    locationClient.lastLocation
                        .addOnSuccessListener { cached ->
                            if (cached != null) {
                                publishLocation(cached, "$reason-cached")
                            } else {
                                dispatchLocationError("Не удалось получить геолокацию.")
                            }
                        }
                        .addOnFailureListener { error ->
                            dispatchLocationError(error.message ?: "Не удалось получить геолокацию.")
                        }
                }
            }
            .addOnFailureListener { error ->
                dispatchLocationError(error.message ?: "Не удалось получить геолокацию.")
            }
    }

    private fun publishLocation(location: Location, reason: String) {
        val json = JSONObject().apply {
            put("lat", location.latitude)
            put("lng", location.longitude)
            put("accuracy", location.accuracy.toDouble())
            put("updatedAt", Instant.now().toString())
            put("source", "android:$reason")
        }.toString()
        lastLocationJson = json
        dispatchToWeb("window.__onNativeLocation(${JSONObject.quote(json)});")
    }

    private fun dispatchLocationError(message: String) {
        dispatchToWeb("window.__onNativeLocationError(${JSONObject.quote(message)});")
    }

    private fun dispatchToWeb(script: String) {
        activity.runOnUiThread {
            webView.evaluateJavascript(script, null)
        }
    }

    private fun reportsDirectory(): File {
        val base = activity.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: activity.filesDir
        return File(base, "reports").apply { mkdirs() }
    }

    private fun shareFile(file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(activity, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    private fun tryStartActivity(intent: Intent): Boolean {
        return try {
            activity.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun sanitizeFileName(input: String): String {
        return input.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
}
