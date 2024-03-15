package com.oldguy.kiscmp

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import platform.Foundation.*

/**
 * A Charset instance can encode a String to bytes or decode bytes to a String using the specified character set.
 * @param set from the enum class of supported character sets
 */
@OptIn(ExperimentalForeignApi::class)
internal actual class Charset actual constructor(set: Charsets) {
    actual val charset = set
    private val nsEnc = when (set) {
        Charsets.Utf8 -> NSUTF8StringEncoding
        Charsets.Utf16le -> NSUTF16LittleEndianStringEncoding
        Charsets.Utf16be -> NSUTF16BigEndianStringEncoding
        Charsets.Iso8859_1 -> NSISOLatin1StringEncoding
        Charsets.UsAscii -> NSASCIIStringEncoding
    }

    /**
     * Using the current character set, decode the entire ByteArray into a String
     * @param bytes For 8 bit character sets, has the same size as the number of characters. For 16-bit character sets,
     * bytes.size is double the number of String characters. Entire content is decoded.
     * @return decoded String
     */
    @OptIn(BetaInteropApi::class)
    actual fun decode(bytes: ByteArray): String {
        val nsData = bytes.usePinned {
            NSData.dataWithBytes(it.addressOf(0), it.get().size.toULong())
        }
        memScoped {
            return NSString.create(nsData, nsEnc).toString()
        }
    }

    /**
     * Using the current character set, encode the entire String into a ByteArray.
     * @param inString
     * @return For 8 bit character sets, a VyteArray with the same size as the length of the String. For 16-bit character sets,
     * ByteArray Size is double the number of String characters.
     */
    actual fun encode(inString: String): ByteArray {
        val err = IllegalArgumentException("String could not be encoded with $charset")
        @Suppress("CAST_NEVER_SUCCEEDS")
        (inString as NSString).dataUsingEncoding(nsEnc)?.let {
            return it.bytes?.readBytes(it.length.toInt())
                ?: throw err
        }
        throw err
    }

}