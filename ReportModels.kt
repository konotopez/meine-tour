package com.artem.medtracker

import org.json.JSONArray
import org.json.JSONObject

data class LocationSnapshot(
    val lat: Double,
    val lng: Double,
    val accuracy: Double,
    val updatedAt: String,
    val source: String,
)

data class ReportRow(
    val name: String,
    val von: String,
    val bis: String,
)

data class ReportPayload(
    val title: String,
    val dateRu: String,
    val dateDe: String,
    val generatedAt: String,
    val totalDuration: String,
    val totalHoursDecimal: String,
    val location: LocationSnapshot?,
    val rows: List<ReportRow>,
)

fun parseReportPayload(json: String): ReportPayload {
    val root = JSONObject(json)
    val rowsArray = root.optJSONArray("rows") ?: JSONArray()
    val rows = buildList {
        for (index in 0 until rowsArray.length()) {
            val item = rowsArray.optJSONObject(index) ?: continue
            add(
                ReportRow(
                    name = item.optString("name"),
                    von = item.optString("von"),
                    bis = item.optString("bis"),
                )
            )
        }
    }

    val location = root.optJSONObject("location")?.let { loc ->
        if (!loc.has("lat") || !loc.has("lng")) {
            null
        } else {
            LocationSnapshot(
                lat = loc.optDouble("lat"),
                lng = loc.optDouble("lng"),
                accuracy = loc.optDouble("accuracy", 0.0),
                updatedAt = loc.optString("updatedAt"),
                source = loc.optString("source"),
            )
        }
    }

    return ReportPayload(
        title = root.optString("title", "Shift Report"),
        dateRu = root.optString("dateRu"),
        dateDe = root.optString("dateDe"),
        generatedAt = root.optString("generatedAt"),
        totalDuration = root.optString("totalDuration"),
        totalHoursDecimal = root.optString("totalHoursDecimal"),
        location = location,
        rows = rows,
    )
}
