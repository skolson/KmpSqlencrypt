package com.oldguy.kiscmp

import java.nio.ByteBuffer
import java.nio.CharBuffer

internal actual class Charset actual constructor(set: Charsets) {
    actual val charset:Charsets = set
    val javaCharset: java.nio.charset.Charset = java.nio.charset.Charset.forName(set.charsetName)

    private val encoder = javaCharset.newEncoder()
    private val decoder = javaCharset.newDecoder()

    actual fun decode(bytes: ByteArray): String {
        return decoder.decode(ByteBuffer.wrap(bytes)).toString()
    }

    actual fun encode(inString: String): ByteArray {
        return if (charset == Charsets.Utf8)
            inString.encodeToByteArray()
        else {
            val buf = encoder.encode(CharBuffer.wrap(inString))
            ByteArray(buf.limit()).apply { buf.get(this) }
        }
    }
}