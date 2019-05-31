/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.stubs.index

import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.util.PathUtil
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.ext.includingFilePath
import org.rust.lang.core.stubs.RsFileStub
import org.rust.lang.core.stubs.RsMacroCallStub

class RsIncludeMacroIndex : StringStubIndexExtension<RsMacroCall>() {
    override fun getVersion(): Int = RsFileStub.Type.stubVersion
    override fun getKey(): StubIndexKey<String, RsMacroCall> = KEY

    companion object {
        val KEY : StubIndexKey<String, RsMacroCall> =
            StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RsIncludeMacroIndex")

        fun index(stub: RsMacroCallStub, indexSink: IndexSink) {
            val key = key(stub.psi) ?: return
            indexSink.occurrence(KEY, key)
        }

        private fun key(call: RsMacroCall): String? {
            val path = call.includingFilePath ?: return null
            return PathUtil.getFileName(path)
        }
    }
}
