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
