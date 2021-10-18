/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.stubs

import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import org.rust.stdext.HashCode
import org.rust.stdext.readHashCodeNullable
import org.rust.stdext.writeHashCodeNullable

class RsProcMacroStubInfo(
    val stubbedText: String,
    val stubbedTextHash: HashCode?,
    val endOfAttrsOffset: Int,
    val startOffset: Int,
) {
    companion object {
        fun serialize(info: RsProcMacroStubInfo?, dataStream: StubOutputStream) {
            if (info == null) {
                dataStream.writeBoolean(false)
                return
            }
            dataStream.writeBoolean(true)

            with(dataStream) {
                writeUTFFast(info.stubbedText)
                writeHashCodeNullable(info.stubbedTextHash)
                writeVarInt(info.endOfAttrsOffset)
                writeVarInt(info.startOffset)
            }
        }

        fun deserialize(dataStream: StubInputStream): RsProcMacroStubInfo? {
            if (!dataStream.readBoolean()) {
                return null
            }
            return RsProcMacroStubInfo(
                dataStream.readUTFFast(),
                dataStream.readHashCodeNullable(),
                dataStream.readVarInt(),
                dataStream.readVarInt(),
            )
        }
    }
}
