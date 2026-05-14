package com.wardrive.analyzer.android.ingest

import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class PcapSummary(
    val packetCount: Int,
    val eapolCount: Int,
    val totalBytes: Long
)

class PcapIngestService {
    fun summarize(inputStream: InputStream): PcapSummary {
        val stream = BufferedInputStream(inputStream)
        val header = ByteArray(24)
        if (stream.read(header) != 24) {
            return PcapSummary(0, 0, 0)
        }
        val order = when {
            header[0] == 0xa1.toByte() && header[1] == 0xb2.toByte() && header[2] == 0xc3.toByte() && header[3] == 0xd4.toByte() -> ByteOrder.BIG_ENDIAN
            header[0] == 0xd4.toByte() && header[1] == 0xc3.toByte() && header[2] == 0xb2.toByte() && header[3] == 0xa1.toByte() -> ByteOrder.LITTLE_ENDIAN
            else -> ByteOrder.LITTLE_ENDIAN
        }

        var packetCount = 0
        var eapolCount = 0
        var totalBytes = 24L
        val pktHeader = ByteArray(16)
        while (true) {
            val readHeader = stream.read(pktHeader)
            if (readHeader < 16) break
            totalBytes += 16
            val inclLen = ByteBuffer.wrap(pktHeader, 8, 4).order(order).int
            if (inclLen <= 0 || inclLen > 10_000_000) break
            val payload = ByteArray(inclLen)
            val readPayload = stream.read(payload)
            if (readPayload != inclLen) break
            totalBytes += inclLen.toLong()
            packetCount += 1
            if (containsEapol(payload)) {
                eapolCount += 1
            }
        }
        return PcapSummary(packetCount, eapolCount, totalBytes)
    }

    private fun containsEapol(data: ByteArray): Boolean {
        if (data.size < 2) return false
        for (i in 0 until data.size - 1) {
            if (data[i] == 0x88.toByte() && data[i + 1] == 0x8e.toByte()) {
                return true
            }
        }
        return false
    }
}

