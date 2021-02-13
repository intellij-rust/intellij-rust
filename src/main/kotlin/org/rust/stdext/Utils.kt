/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */


package org.rust.stdext

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import org.apache.commons.lang.RandomStringUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.streams.asSequence

private val LOG = Logger.getInstance("#org.rust.stdext")

/**
 * Just a way to force exhaustiveness analysis for Kotlin's `when` expression.
 *
 * Use it like this:
 * ```
 * when (foo) {
 *     is Bar -> {}
 *     is Baz -> {}
 * }.exhaustive // ensure `Bar` and `Baz` are the only variants of `foo`
 * ```
 */
val <T> T.exhaustive: T
    inline get() = this

inline fun <T> VirtualFile.applyWithSymlink(f: (VirtualFile) -> T?): T? {
    return f(this) ?: f(canonicalFile ?: return null)
}

fun String.toPath(): Path = Paths.get(this)

fun String.toPathOrNull(): Path? = pathOrNull(this::toPath)

fun Path.resolveOrNull(other: String): Path? = pathOrNull { resolve(other) }

private inline fun pathOrNull(block: () -> Path): Path? {
    return try {
        block()
    } catch (e: InvalidPathException) {
        LOG.warn(e)
        null
    }
}

fun Path.isExecutable(): Boolean = Files.isExecutable(this)

fun Path.list(): Sequence<Path> = Files.list(this).asSequence()

fun String.pluralize(): String = StringUtil.pluralize(this)

fun randomLowercaseAlphabetic(length: Int): String =
    RandomStringUtils.random(length, "0123456789abcdefghijklmnopqrstuvwxyz")

fun ByteArray.getLeading64bits(): Long =
    ByteBuffer.wrap(this).also { it.order(ByteOrder.BIG_ENDIAN) }.getLong(0)
