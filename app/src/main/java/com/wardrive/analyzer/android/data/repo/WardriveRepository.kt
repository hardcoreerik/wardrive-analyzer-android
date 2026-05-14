package com.wardrive.analyzer.android.data.repo

import com.wardrive.analyzer.android.data.db.WardriveDatabase
import com.wardrive.analyzer.android.data.model.EvidenceEntity
import com.wardrive.analyzer.android.data.model.ReportEntity
import com.wardrive.analyzer.android.data.model.RunEntity
import com.wardrive.analyzer.android.ingest.PcapIngestService
import com.wardrive.analyzer.android.ingest.WardriveLogParser
import java.io.BufferedReader
import java.io.InputStream
import java.util.UUID

class WardriveRepository(
    private val db: WardriveDatabase,
    private val parser: WardriveLogParser = WardriveLogParser(),
    private val pcapIngestService: PcapIngestService = PcapIngestService()
) {
    val evidence = db.evidenceDao().observeAll()
    val evidenceCount = db.evidenceDao().observeCount()
    val openCount = db.evidenceDao().observeOpenCount()
    val runs = db.runDao().observeAll()
    val reports = db.reportDao().observeAll()

    suspend fun importLog(sourceName: String, reader: BufferedReader): Int {
        val parsed = parser.parse(reader)
        if (parsed.isEmpty()) return 0
        val batch = UUID.randomUUID().toString()
        val importedAt = System.currentTimeMillis()
        val rows = parsed.map {
            EvidenceEntity(
                sourceName = sourceName,
                bssid = it.bssid,
                ssid = it.ssid,
                security = it.security,
                channel = it.channel,
                rssi = it.rssi,
                latitude = it.latitude,
                longitude = it.longitude,
                seenAt = it.seenAt,
                importedAt = importedAt,
                importBatch = batch
            )
        }
        db.evidenceDao().insertAll(rows)
        val openNetworks = rows.count { it.security.contains("OPEN", ignoreCase = true) }
        val hiddenNetworks = rows.count { it.ssid == "<hidden>" }
        val risk = computeRiskScore(
            openNetworks = openNetworks,
            hiddenNetworks = hiddenNetworks,
            pcapEapol = 0,
            apCount = rows.map { it.bssid }.toSet().size,
            stationCount = 0
        )

        val runId = db.runDao().insert(
            RunEntity(
                name = "Import $sourceName",
                createdAt = importedAt,
                evidenceCount = rows.size,
                openNetworkCount = openNetworks,
                hiddenSsidCount = hiddenNetworks,
                pcapPacketCount = 0,
                pcapEapolCount = 0,
                pcapBytes = 0,
                pcapApCount = 0,
                pcapStationCount = 0,
                handshakeConfidence = "NONE",
                riskScore = risk
            )
        )
        db.reportDao().insertAll(
            listOf(
                ReportEntity(
                    runId = runId,
                    reportType = "summary",
                    title = "Run Summary",
                    body = "Imported ${rows.size} rows from $sourceName. Risk score: $risk/100.",
                    createdAt = importedAt
                )
            )
        )
        return rows.size
    }

    suspend fun importPcap(sourceName: String, inputStream: InputStream): Int {
        val summary = pcapIngestService.summarize(inputStream)
        val importedAt = System.currentTimeMillis()
        val risk = computeRiskScore(
            openNetworks = 0,
            hiddenNetworks = 0,
            pcapEapol = summary.eapolCount,
            apCount = summary.apCount,
            stationCount = summary.stationCount,
            handshakeConfidence = summary.handshakeConfidence
        )
        val runId = db.runDao().insert(
            RunEntity(
                name = "PCAP $sourceName",
                createdAt = importedAt,
                evidenceCount = 0,
                openNetworkCount = 0,
                hiddenSsidCount = 0,
                pcapPacketCount = summary.packetCount,
                pcapEapolCount = summary.eapolCount,
                pcapBytes = summary.totalBytes,
                pcapApCount = summary.apCount,
                pcapStationCount = summary.stationCount,
                handshakeConfidence = summary.handshakeConfidence,
                riskScore = risk
            )
        )
        db.reportDao().insertAll(
            listOf(
                ReportEntity(
                    runId = runId,
                    reportType = "pcap_summary",
                    title = "PCAP Summary",
                    body = "Packets=${summary.packetCount}, EAPOL=${summary.eapolCount}, Handshake=${summary.handshakeConfidence}, APs=${summary.apCount}, Stations=${summary.stationCount}, MgmtFrames=${summary.managementFrameCount}, Bytes=${summary.totalBytes}. Risk score: $risk/100.",
                    createdAt = importedAt
                )
            )
        )
        return summary.packetCount
    }

    private fun computeRiskScore(
        openNetworks: Int,
        hiddenNetworks: Int,
        pcapEapol: Int,
        apCount: Int,
        stationCount: Int,
        handshakeConfidence: String = "NONE"
    ): Int {
        val openRisk = (openNetworks * 3).coerceAtMost(35)
        val hiddenRisk = (hiddenNetworks * 2).coerceAtMost(15)
        val eapolRisk = (pcapEapol / 2).coerceAtMost(30)
        val densityRisk = ((apCount + stationCount) / 5).coerceAtMost(20)
        val handshakeRisk = when (handshakeConfidence) {
            "4WAY" -> 20
            "PARTIAL" -> 10
            "EAPOL" -> 4
            else -> 0
        }
        return (openRisk + hiddenRisk + eapolRisk + densityRisk + handshakeRisk).coerceIn(0, 100)
    }
}
