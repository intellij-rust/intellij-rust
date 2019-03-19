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
import org.rust.lang.core.psi.RsUseItem
import org.rust.lang.core.psi.RsUseSpeck
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.nameInScope
import org.rust.lang.core.psi.ext.pathOrQualifier
import org.rust.lang.core.stubs.RsFileStub
import org.rust.lang.core.stubs.RsUseSpeckStub
import org.rust.openapiext.getElements

class RsReexportIndex : StringStubIndexExtension<RsUseSpeck>() {
    override fun getVersion(): Int = RsFileStub.Type.stubVersion
    override fun getKey(): StubIndexKey<String, RsUseSpeck> = KEY

    companion object {
        val KEY: StubIndexKey<String, RsUseSpeck> =
            StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RsReexportIndex")

        fun index(stub: RsUseSpeckStub, sink: IndexSink) {
            val useSpeck = stub.psi
            val isPublic = useSpeck.ancestorStrict<RsUseItem>()?.vis != null
            if (!isPublic) return
            val name = if (stub.isStarImport) {
                useSpeck.pathOrQualifier?.referenceName
            } else {
                useSpeck.nameInScope
            } ?: return
            sink.occurrence(KEY, name)
        }

        fun findReexportsByName(
            project: Project,
            target: String,
            scope: GlobalSearchScope = GlobalSearchScope.allScope(project)
        ): Collection<RsUseSpeck> = getElements(KEY, target, project, scope)
    }
}
