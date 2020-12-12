/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.stubs.index

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.lang.core.RsPsiPattern
import org.rust.lang.core.psi.RsMetaItem
import org.rust.lang.core.psi.RsMetaItemArgs
import org.rust.lang.core.psi.ext.ancestors
import org.rust.lang.core.psi.ext.name
import org.rust.lang.core.psi.rustStructureModificationTracker
import org.rust.lang.core.stubs.RsFileStub
import org.rust.lang.core.stubs.RsMetaItemStub
import org.rust.openapiext.checkCommitIsNotInProgress

/**
 * An index for `test` cfg options in any negative context like
 * `#[cfg(not(test))]` or `#[cfg(not(and(or(test, ...), ...)))]`
 */
class RsCfgNotTestIndex : StringStubIndexExtension<RsMetaItem>() {
    override fun getKey(): StubIndexKey<String, RsMetaItem> = KEY
    override fun getVersion(): Int = RsFileStub.Type.stubVersion

    companion object {
        private val KEY: StubIndexKey<String, RsMetaItem> =
            StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RsCfgIndex")

        private const val NOT_TEST: String = "#"

        fun index(stub: RsMetaItemStub, sink: IndexSink) {
            if (isCfgNotTest(stub.psi)) {
                sink.occurrence(KEY, NOT_TEST)
            }
        }

        // `#[cfg(not(test))]`
        fun hasCfgNotTest(project: Project, pkg: CargoWorkspace.Package): Boolean {
            return CachedValuesManager.getManager(project).getCachedValue(pkg) {
                val contentRoot = pkg.contentRoot
                val result = if (contentRoot != null) {
                    hasCfgNotTest(project, GlobalSearchScopesCore.DirectoryScope(project, contentRoot, true))
                } else {
                    false
                }
                CachedValueProvider.Result.create(
                    result,
                    project.rustStructureModificationTracker
                )
            }
        }

        private fun hasCfgNotTest(project: Project, scope: GlobalSearchScope): Boolean {
            checkCommitIsNotInProgress(project)
            return !StubIndex.getInstance().processElements(KEY, NOT_TEST, project, scope, RsMetaItem::class.java) {
                false
            }
        }

        @VisibleForTesting
        fun isCfgNotTest(psi: RsMetaItem): Boolean {
            if (psi.name != "test") return false
            val parent = psi.parent as? RsMetaItemArgs ?: return false
            val not = parent.ancestors.find { it is RsMetaItem && it.name == "not" } ?: return false
            return not.ancestors.any { it is RsMetaItem && RsPsiPattern.anyCfgCondition.accepts(it) }
        }
    }
}
