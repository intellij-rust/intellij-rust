/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.stubs.index

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.rust.lang.core.psi.RsExternCrateItem
import org.rust.lang.core.psi.ext.isPublic
import org.rust.lang.core.stubs.RsExternCrateItemStub
import org.rust.lang.core.stubs.RsFileStub
import org.rust.openapiext.getElements

class RsExternCrateReexportIndex : StringStubIndexExtension<RsExternCrateItem>() {
    override fun getVersion(): Int = RsFileStub.Type.stubVersion
    override fun getKey(): StubIndexKey<String, RsExternCrateItem> = KEY

    companion object {
        val KEY: StubIndexKey<String, RsExternCrateItem> =
            StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RsExternCrateReexportIndex")

        fun index(stub: RsExternCrateItemStub, sink: IndexSink) {
            val externCrateItem = stub.psi
            if (!externCrateItem.isPublic) return
            sink.occurrence(KEY, externCrateItem.referenceName)
        }

        fun findReexportsByName(project: Project, name: String): Collection<RsExternCrateItem> {
            return getElements(KEY, name, project, GlobalSearchScope.allScope(project))
        }
    }
}
