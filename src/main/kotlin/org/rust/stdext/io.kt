/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.stdext

import com.intellij.util.io.DataInputOutputUtil
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

@Throws(IOException::class)
fun DataInput.readVarInt(): Int =
    DataInputOutputUtil.readINT(this)

@Throws(IOException::class)
fun DataOutput.writeVarInt(value: Int): Unit =
    DataInputOutputUtil.writeINT(this, value)
