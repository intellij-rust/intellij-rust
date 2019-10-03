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
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.stubs.RsFileStub
import org.rust.openapiext.checkCommitIsNotInProgress
import org.rust.openapiext.getElements

class RsMacroCallIndex : StringStubIndexExtension<RsMacroCall>() {

    override fun getVersion(): Int = RsFileStub.Type.stubVersion
    override fun getKey(): StubIndexKey<String, RsMacroCall> = KEY

    companion object {

        private val KEY: StubIndexKey<String, RsMacroCall> =
            StubIndexKey.createIndexKey("org.rust.lang.core.resolve.indexes.RsMacroCallIndex")

        private const val SINGLE_KEY = "#"

        fun index(sink: IndexSink) {
            sink.occurrence(KEY, SINGLE_KEY)
        }

        fun getMacroCalls(
            project: Project,
            scope: GlobalSearchScope = GlobalSearchScope.allScope(project)
        ): Collection<RsMacroCall> {
            checkCommitIsNotInProgress(project)
            return getElements(KEY, SINGLE_KEY, project, scope)
        }
    }
}
