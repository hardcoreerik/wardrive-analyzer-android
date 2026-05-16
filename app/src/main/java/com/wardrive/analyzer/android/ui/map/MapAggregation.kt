package com.wardrive.analyzer.android.ui.map

import androidx.compose.ui.geometry.Offset
import com.wardrive.analyzer.android.data.model.EvidenceEntity
import com.wardrive.analyzer.android.data.model.MarauderWardriveRecordEntity
import com.wardrive.analyzer.android.data.model.ReportEntity
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object MapAggregation {
    private const val EARTH_RADIUS_KM = 6371.0

    data class IsoProjectionResult(
        val normalized: List<Offset>,
        val isoPoints: List<Offset>
    )

    fun buildState(
        evidence: List<EvidenceEntity>,
        wardrive: List<MarauderWardriveRecordEntity>,
        reports: List<ReportEntity>,
        runsCount: Int,
        filter: MapEntityTypeFilter,
        selectedEntityId: String?,
        viewport: MapViewport
    ): MapScreenState {
        val mapPoints = mutableListOf<MapEntityPoint>()
        evidence.forEach { row ->
            val lat = row.latitude
            val lon = row.longitude
            if (lat == null || lon == null) return@forEach
            mapPoints += MapEntityPoint(
                id = "e_${row.id}",
                type = if (row.security.contains("BLE", true)) MapEntityType.BLE else MapEntityType.WIFI,
                latitude = lat,
                longitude = lon,
                title = row.ssid.ifBlank { "<hidden>" },
                subtitle = row.security,
                bssid = row.bssid,
                security = row.security,
                channel = row.channel,
                rssi = row.rssi,
                seenAt = row.seenAt
            )
            if (row.security.contains("HANDSHAKE", true)) {
                mapPoints += MapEntityPoint(
                    id = "h_${row.id}",
                    type = MapEntityType.HANDSHAKE,
                    latitude = lat,
                    longitude = lon,
                    title = row.ssid.ifBlank { "<hidden>" },
                    subtitle = "WPA handshake",
                    bssid = row.bssid,
                    security = row.security,
                    channel = row.channel,
                    rssi = row.rssi,
                    seenAt = row.seenAt
                )
            }
            mapPoints += MapEntityPoint(
                id = "g_${row.id}",
                type = MapEntityType.GPS,
                latitude = lat,
                longitude = lon,
                title = "GPS Fix",
                subtitle = "Imported telemetry",
                bssid = null,
                security = null,
                channel = null,
                rssi = null,
                seenAt = row.seenAt
            )
        }

        wardrive.forEach { row ->
            val lat = row.latitude
            val lon = row.longitude
            if (lat == null || lon == null) return@forEach
            val inferredType = when {
                (row.encryption ?: "").contains("ble", true) -> MapEntityType.BLE
                (row.encryption ?: "").contains("hs", true) || (row.rawLine).contains("handshake", true) -> MapEntityType.HANDSHAKE
                else -> MapEntityType.WIFI
            }
            mapPoints += MapEntityPoint(
                id = "m_${row.id}",
                type = inferredType,
                latitude = lat,
                longitude = lon,
                title = row.ssid ?: "Unknown SSID",
                subtitle = row.encryption ?: "Unknown security",
                bssid = row.bssid,
                security = row.encryption,
                channel = row.channel,
                rssi = row.rssi,
                seenAt = row.timestamp
            )
            mapPoints += MapEntityPoint(
                id = "mg_${row.id}",
                type = MapEntityType.GPS,
                latitude = lat,
                longitude = lon,
                title = "GPS Fix",
                subtitle = "Wardrive route",
                bssid = null,
                security = null,
                channel = null,
                rssi = null,
                seenAt = row.timestamp
            )
        }

        val sortedGeo = mapPoints
            .sortedBy { it.seenAt ?: Long.MAX_VALUE }
            .distinctBy { "${it.latitude},${it.longitude}" }

        val routeDistance = routeDistanceKm(sortedGeo.map { it.latitude to it.longitude })
        val stats = MapSummaryStats(
            sessionCount = runsCount,
            apCount = mapPoints.count { it.type == MapEntityType.WIFI },
            bleCount = mapPoints.count { it.type == MapEntityType.BLE },
            handshakeCount = mapPoints.count { it.type == MapEntityType.HANDSHAKE },
            gpsFixCount = mapPoints.count { it.type == MapEntityType.GPS },
            distanceKm = routeDistance,
            strongestSignalDbm = mapPoints.mapNotNull { it.rssi }.maxOrNull()
        )

        val filtered = mapPoints.filter { pointMatchesFilter(it, filter) }
        val selected = mapPoints.firstOrNull { it.id == selectedEntityId }

        val isoRoute = if (sortedGeo.isEmpty()) emptyList() else {
            val projected = projectToIsometric(sortedGeo.map { it.latitude to it.longitude })
            projected.isoPoints
        }

        return MapScreenState(
            hasGeoData = mapPoints.isNotEmpty(),
            entities = mapPoints,
            filteredEntities = filtered,
            route = MapRoutePath(points = isoRoute, distanceKm = routeDistance),
            stats = stats,
            filter = filter,
            selectedEntityId = selectedEntityId,
            selectedEntity = selected,
            recentActivity = reports.take(5).map { it.title },
            viewport = viewport
        )
    }

    fun projectToIsometric(points: List<Pair<Double, Double>>): IsoProjectionResult {
        if (points.isEmpty()) return IsoProjectionResult(emptyList(), emptyList())
        val latMin = points.minOf { it.first }
        val latMax = points.maxOf { it.first }
        val lonMin = points.minOf { it.second }
        val lonMax = points.maxOf { it.second }
        val latSpan = maxOf(0.0000001, latMax - latMin)
        val lonSpan = maxOf(0.0000001, lonMax - lonMin)

        val normalized = points.map { (lat, lon) ->
            val x = ((lon - lonMin) / lonSpan).toFloat()
            val y = ((lat - latMin) / latSpan).toFloat()
            Offset(x, y)
        }

        val iso = normalized.map { p ->
            val cx = p.x - 0.5f
            val cy = p.y - 0.5f
            val isoX = (cx - cy)
            val isoY = (cx + cy) * 0.55f
            Offset(isoX + 0.5f, isoY + 0.5f)
        }

        return IsoProjectionResult(normalized, iso)
    }

    fun routeDistanceKm(path: List<Pair<Double, Double>>): Double {
        if (path.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until path.size) {
            total += haversineKm(path[i - 1], path[i])
        }
        return total
    }

    private fun pointMatchesFilter(point: MapEntityPoint, filter: MapEntityTypeFilter): Boolean {
        return when (filter) {
            MapEntityTypeFilter.ALL -> true
            MapEntityTypeFilter.WIFI -> point.type == MapEntityType.WIFI
            MapEntityTypeFilter.BLE -> point.type == MapEntityType.BLE
            MapEntityTypeFilter.HANDSHAKES -> point.type == MapEntityType.HANDSHAKE
            MapEntityTypeFilter.GPS -> point.type == MapEntityType.GPS
        }
    }

    private fun haversineKm(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val lat1 = Math.toRadians(a.first)
        val lon1 = Math.toRadians(a.second)
        val lat2 = Math.toRadians(b.first)
        val lon2 = Math.toRadians(b.second)
        val dLat = lat2 - lat1
        val dLon = lon2 - lon1
        val h = sin(dLat / 2).let { it * it } + cos(lat1) * cos(lat2) * sin(dLon / 2).let { it * it }
        val c = 2 * atan2(sqrt(h), sqrt(1 - h))
        return EARTH_RADIUS_KM * c
    }
}
