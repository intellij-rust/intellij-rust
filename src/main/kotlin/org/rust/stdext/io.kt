/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.stdext

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.DataInputOutputUtil
import java.io.*

@Throws(IOException::class)
fun DataInput.readVarInt(): Int =
    DataInputOutputUtil.readINT(this)

@Throws(IOException::class)
fun DataOutput.writeVarInt(value: Int): Unit =
    DataInputOutputUtil.writeINT(this, value)

@Throws(IOException::class)
fun OutputStream.writeStream(input: InputStream): Unit =
    FileUtil.copy(input, this)

@Throws(IOException::class)
fun <E : Enum<E>> DataOutput.writeEnum(e: E) = writeByte(e.ordinal)

@Throws(IOException::class)
inline fun <reified E : Enum<E>> DataInput.readEnum(): E = enumValues<E>()[readUnsignedByte()]

@Throws(IOException::class)
fun <T, E> DataInput.readRsResult(
    okReader: DataInput.() -> T,
    errReader: DataInput.() -> E
): RsResult<T, E> = when (readBoolean()) {
    true -> RsResult.Ok(okReader())
    false -> RsResult.Err(errReader())
}

@Throws(IOException::class)
fun <T, E> DataOutput.writeRsResult(
    value: RsResult<T, E>,
    okWriter: DataOutput.(T) -> Unit,
    errWriter: DataOutput.(E) -> Unit
): Unit = when (value) {
    is RsResult.Ok -> {
        writeBoolean(true)
        okWriter(value.ok)
    }
    is RsResult.Err -> {
        writeBoolean(false)
        errWriter(value.err)
    }
}
