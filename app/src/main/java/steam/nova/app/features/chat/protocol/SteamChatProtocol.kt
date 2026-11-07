package steam.nova.app.features.chat.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream

/** Steam's protobuf wire format. The CM field mapping follows Monica Steam's Kotlin implementation. */
internal object SteamChatProtocol {
    const val MAX_PACKET = 4 * 1024 * 1024
    private const val MAX_EXPANDED = 16 * 1024 * 1024

    class Field(val number: Long = 0, val bytes: ByteArray = byteArrayOf()) {
        fun text(): String = bytes.toString(Charsets.UTF_8)
        fun fields(): Fields = read(bytes)
    }

    class Fields(private val items: Map<Int, List<Field>>) {
        fun number(id: Int, default: Long = 0): Long = items[id]?.firstOrNull()?.number ?: default
        fun text(id: Int): String = items[id]?.firstOrNull()?.text().orEmpty()
        fun bytes(id: Int): ByteArray = items[id]?.firstOrNull()?.bytes ?: byteArrayOf()
        fun messages(id: Int): List<Fields> = items[id].orEmpty().map { it.fields() }
        fun has(id: Int): Boolean = items.containsKey(id)
        fun numbers(id: Int): List<Long> = items[id].orEmpty().flatMap { field ->
            if (field.bytes.isEmpty()) listOf(field.number) else {
                val values = mutableListOf<Long>()
                var offset = 0
                while (offset < field.bytes.size) {
                    var value = 0L
                    var complete = false
                    for (shift in 0..63 step 7) {
                        require(offset < field.bytes.size)
                        val next = field.bytes[offset++].toInt() and 255
                        require(shift != 63 || next <= 1)
                        value = value or ((next and 127).toLong() shl shift)
                        if (next and 128 == 0) { complete = true; break }
                    }
                    require(complete); values.add(value)
                }
                values
            }
        }
    }

    class Writer {
        private val out = ByteArrayOutputStream()
        private fun unsigned(value: Long) {
            var rest = value
            while (rest and -128L != 0L) {
                out.write((rest.toInt() and 127) or 128)
                rest = rest ushr 7
            }
            out.write(rest.toInt())
        }
        fun number(id: Int, value: Long): Writer = apply { unsigned(id.toLong() shl 3); unsigned(value) }
        fun fixed64(id: Int, value: Long): Writer = apply {
            unsigned((id.toLong() shl 3) or 1)
            repeat(8) { out.write((value ushr (it * 8)).toInt() and 255) }
        }
        fun fixed32(id: Int, value: Long): Writer = apply {
            unsigned((id.toLong() shl 3) or 5)
            repeat(4) { out.write((value ushr (it * 8)).toInt() and 255) }
        }
        fun text(id: Int, value: String): Writer = bytes(id, value.toByteArray(Charsets.UTF_8))
        fun bytes(id: Int, value: ByteArray): Writer = apply {
            unsigned((id.toLong() shl 3) or 2); unsigned(value.size.toLong()); out.write(value)
        }
        fun build(): ByteArray = out.toByteArray()
    }

    fun read(bytes: ByteArray): Fields {
        require(bytes.size <= MAX_EXPANDED)
        val input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        fun unsigned(): Long {
            var value = 0L
            for (shift in 0..63 step 7) {
                require(input.hasRemaining()) { "Truncated protobuf" }
                val next = input.get().toInt() and 255
                require(shift != 63 || next <= 1) { "Invalid protobuf integer" }
                value = value or ((next and 127).toLong() shl shift)
                if (next and 128 == 0) return value
            }
            error("Invalid protobuf integer")
        }
        val fields = linkedMapOf<Int, MutableList<Field>>()
        var count = 0
        while (input.hasRemaining()) {
            require(++count <= 100_000)
            val tag = unsigned()
            val id = (tag ushr 3).toInt()
            val wire = (tag and 7).toInt()
            require(id in 1..536_870_911)
            val field = when (wire) {
                0 -> Field(unsigned())
                1 -> { require(input.remaining() >= 8); Field(input.long) }
                2 -> {
                    val size = unsigned()
                    require(size in 0..input.remaining().toLong())
                    Field(bytes = ByteArray(size.toInt()).also(input::get))
                }
                5 -> { require(input.remaining() >= 4); Field(input.int.toLong() and 0xffffffffL) }
                else -> error("Unsupported protobuf wire type")
            }
            fields.getOrPut(id) { mutableListOf() }.add(field)
        }
        return Fields(fields)
    }

    class Packet(val type: Int, val header: Fields, val body: ByteArray)

    fun packet(type: Int, steamId: Long, session: Int, body: ByteArray, job: Long = -1, method: String? = null): ByteArray {
        val header = Writer().fixed64(1, steamId).number(2, session.toLong())
            .fixed64(10, job).fixed64(11, -1).apply { method?.let { text(12, it) } }.build()
        return ByteBuffer.allocate(8 + header.size + body.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(type or Int.MIN_VALUE).putInt(header.size).put(header).put(body).array()
    }

    fun logon(token: String): ByteArray = Writer().number(1, 65_580).number(7, 4_294_966_596)
        // MobileChat, retaining the verified web nonce/OS credential flow.
        .number(32, 5).number(33, 2).text(80, "anonymous").text(103, token).build()

    fun decode(bytes: ByteArray): List<Packet> {
        require(bytes.size <= MAX_PACKET)
        val result = mutableListOf<Packet>()
        var budget = MAX_EXPANDED
        fun decodeOne(data: ByteArray, depth: Int) {
            require(depth <= 6 && data.size >= 8 && data.size <= budget)
            budget -= data.size
            val input = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val raw = input.int
            require(raw and Int.MIN_VALUE != 0)
            val length = input.int
            require(length in 0..input.remaining())
            val header = read(ByteArray(length).also(input::get))
            val body = ByteArray(input.remaining()).also(input::get)
            val type = raw and Int.MAX_VALUE
            if (type != 1) {
                require(result.size < 4096)
                result.add(Packet(type, header, body))
                return
            }
            val multi = read(body)
            val expected = multi.number(1)
            val encoded = multi.bytes(2)
            val payload = if (expected > 0) {
                require(expected <= budget)
                GZIPInputStream(ByteArrayInputStream(encoded)).use { gzip ->
                    val expanded = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val size = gzip.read(buffer)
                        if (size < 0) break
                        require(expanded.size() + size <= expected)
                        expanded.write(buffer, 0, size)
                    }
                    expanded.toByteArray().also { require(it.size.toLong() == expected) }
                }
            } else encoded
            val children = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
            while (children.hasRemaining()) {
                require(children.remaining() >= 4)
                val size = children.int
                require(size in 8..children.remaining())
                decodeOne(ByteArray(size).also(children::get), depth + 1)
            }
        }
        decodeOne(bytes, 0)
        return result
    }
}
