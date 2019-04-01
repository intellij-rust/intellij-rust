/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.stdext

import org.apache.commons.codec.binary.Hex
import java.io.Serializable
import java.security.MessageDigest

/**
 * Abstracts byte array of cryptographic hash to provide appropriate equals/hashCode methods.
 * Alternative to [com.google.common.hash.HashCode] (that shouldn't be used because of @Beta)
 */
/*inline*/ class HashCode private constructor(private val hash: ByteArray) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HashCode

        if (!hash.contentEquals(other.hash)) return false

        return true
    }

    override fun hashCode(): Int {
        return hash.contentHashCode()
    }

    override fun toString(): String {
        return Hex.encodeHexString(hash)
    }

    fun toByteArray(): ByteArray = hash

    companion object {
        const val ARRAY_LEN: Int = 20
        private val SHA1 by ThreadLocalDelegate { MessageDigest.getInstance("SHA-1") }

        fun compute(input: String): HashCode =
            HashCode(SHA1.digest(input.toByteArray()))

        fun fromByteArray(bytes: ByteArray): HashCode {
            check(bytes.size == ARRAY_LEN)
            return HashCode(bytes)
        }
    }
}
