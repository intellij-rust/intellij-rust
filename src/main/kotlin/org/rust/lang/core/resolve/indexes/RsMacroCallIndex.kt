/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.indexes

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.rust.lang.core.psi.ext.RsPossibleMacroCall
import org.rust.lang.core.psi.ext.canBeMacroCall
import org.rust.lang.core.stubs.RsFileStub
import org.rust.lang.core.stubs.RsMetaItemStub
import org.rust.openapiext.checkCommitIsNotInProgress
import org.rust.openapiext.getElements

class RsMacroCallIndex : StringStubIndexExtension<RsPossibleMacroCall>() {

    override fun getVersion(): Int = RsFileStub.Type.stubVersion
    override fun getKey(): StubIndexKey<String, RsPossibleMacroCall> = KEY

    companion object {

        private val KEY: StubIndexKey<String, RsPossibleMacroCall> =
            StubIndexKey.createIndexKey("org.rust.lang.core.resolve.indexes.RsMacroCallIndex")

        private const val SINGLE_KEY = "#"

        fun indexMacroCall(sink: IndexSink) {
            sink.occurrence(KEY, SINGLE_KEY)
        }

        fun indexMetaItem(sink: IndexSink, stub: RsMetaItemStub) {
            if (stub.psi.canBeMacroCall) {
                sink.occurrence(KEY, SINGLE_KEY)
            }
        }

        fun getMacroCalls(
            project: Project,
            scope: GlobalSearchScope = GlobalSearchScope.allScope(project)
        ): Collection<RsPossibleMacroCall> {
            checkCommitIsNotInProgress(project)
            return getElements(KEY, SINGLE_KEY, project, scope)
        }
    }
}
