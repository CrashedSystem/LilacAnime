package com.lilac.anime.network

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** IPv4/UDP DNS proxy for the DNS-only TUN interface. */
class DnsPacketHandler(private val doh: LteDoHClient) {
    companion object {
        private const val TAG = "LilacDns"
        private const val UDP = 17
        private const val DNS_PORT = 53
        private const val IPV4_HEADER_MIN = 20
    }

    fun handle(packet: ByteArray): ByteArray? {
        if (packet.size < IPV4_HEADER_MIN) return null
        val version = (packet[0].toInt() ushr 4) and 0x0f
        val ihl = (packet[0].toInt() and 0x0f) * 4
        if (version != 4 || ihl < IPV4_HEADER_MIN || packet.size < ihl + 8) return null
        if ((packet[9].toInt() and 0xff) != UDP) return null

        val udpOffset = ihl
        val srcPort = u16(packet, udpOffset)
        val dstPort = u16(packet, udpOffset + 2)
        if (dstPort != DNS_PORT) return null

        val udpLength = u16(packet, udpOffset + 4)
        if (udpLength < 8 || udpOffset + udpLength > packet.size) return null

        val dns = packet.copyOfRange(udpOffset + 8, udpOffset + udpLength)
        if (dns.size < 12) return null

        Log.d(TAG, "DNS query id=${u16(dns, 0)} bytes=${dns.size}")
        val answer = doh.query(dns) ?: return null
        return buildResponse(packet, ihl, srcPort, answer)
    }

    private fun buildResponse(
        request: ByteArray,
        ihl: Int,
        clientPort: Int,
        dns: ByteArray
    ): ByteArray {
        val udpLength = 8 + dns.size
        val totalLength = ihl + udpLength
        val out = ByteArray(totalLength)
        System.arraycopy(request, 0, out, 0, ihl)

        // Swap IPv4 source/destination.
        for (i in 0 until 4) {
            val a = request[12 + i]
            out[12 + i] = request[16 + i]
            out[16 + i] = a
        }

        // IPv4 header fields.
        putU16(out, 2, totalLength)
        out[8] = 64.toByte() // TTL
        out[9] = UDP.toByte()
        putU16(out, 10, 0)
        putU16(out, 10, checksum(out, 0, ihl))

        val u = ihl
        putU16(out, u, DNS_PORT)
        putU16(out, u + 2, clientPort)
        putU16(out, u + 4, udpLength)
        putU16(out, u + 6, 0)
        System.arraycopy(dns, 0, out, u + 8, dns.size)
        putU16(out, u + 6, udpChecksum(out, ihl, udpLength))
        return out
    }

    private fun udpChecksum(packet: ByteArray, udpOffset: Int, udpLength: Int): Int {
        var sum = 0L
        sum += ((packet[12].toInt() and 0xff) shl 8) or (packet[13].toInt() and 0xff)
        sum += ((packet[14].toInt() and 0xff) shl 8) or (packet[15].toInt() and 0xff)
        sum += ((packet[16].toInt() and 0xff) shl 8) or (packet[17].toInt() and 0xff)
        sum += ((packet[18].toInt() and 0xff) shl 8) or (packet[19].toInt() and 0xff)
        sum += UDP
        sum += udpLength

        var i = udpOffset
        val end = udpOffset + udpLength
        while (i + 1 < end) {
            if (i == udpOffset + 6) {
                i += 2
                continue
            }
            sum += ((packet[i].toInt() and 0xff) shl 8) or (packet[i + 1].toInt() and 0xff)
            i += 2
        }
        if (i < end) sum += (packet[i].toInt() and 0xff) shl 8

        while ((sum ushr 16) != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        val result = (sum.inv() and 0xffff).toInt()
        return if (result == 0) 0xffff else result
    }

    private fun checksum(packet: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += ((packet[i].toInt() and 0xff) shl 8) or (packet[i + 1].toInt() and 0xff)
            i += 2
        }
        if (i < end) sum += (packet[i].toInt() and 0xff) shl 8
        while ((sum ushr 16) != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        return (sum.inv() and 0xffff).toInt()
    }

    private fun u16(a: ByteArray, p: Int): Int =
        ((a[p].toInt() and 0xff) shl 8) or (a[p + 1].toInt() and 0xff)

    private fun putU16(a: ByteArray, p: Int, value: Int) {
        a[p] = (value ushr 8).toByte()
        a[p + 1] = value.toByte()
    }
}
