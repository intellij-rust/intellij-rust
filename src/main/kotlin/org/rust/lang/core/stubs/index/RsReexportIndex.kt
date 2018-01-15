/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.stubs.index

import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.rust.lang.core.psi.RsUseItem
import org.rust.lang.core.psi.RsUseSpeck
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.nameInScope
import org.rust.lang.core.stubs.RsFileStub
import org.rust.lang.core.stubs.RsUseSpeckStub

class RsReexportIndex : StringStubIndexExtension<RsUseSpeck>() {
    override fun getVersion(): Int = RsFileStub.Type.stubVersion
    override fun getKey(): StubIndexKey<String, RsUseSpeck> = KEY

    companion object {
        val KEY: StubIndexKey<String, RsUseSpeck> =
            StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RsReexportIndex")

        fun index(stub: RsUseSpeckStub, sink: IndexSink) {
            val useSpeck = stub.psi
            val isPublic = useSpeck.ancestorStrict<RsUseItem>()?.isPublic == true
            if (!isPublic) return
            val name = useSpeck.nameInScope ?: return
            sink.occurrence(KEY, name)
        }
    }
}
