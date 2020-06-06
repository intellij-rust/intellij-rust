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
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.rust.lang.core.psi.RsExternCrateItem
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.containingCargoTarget
import org.rust.lang.core.psi.rustStructureOrAnyPsiModificationTracker
import org.rust.lang.core.stubs.RsExternCrateItemStub
import org.rust.lang.core.stubs.RsFileStub
import org.rust.openapiext.checkCommitIsNotInProgress
import org.rust.openapiext.getElements

class RsExternCrateReexportIndex : StringStubIndexExtension<RsExternCrateItem>() {
    override fun getVersion(): Int = RsFileStub.Type.stubVersion
    override fun getKey(): StubIndexKey<String, RsExternCrateItem> = KEY

    companion object {
        val KEY: StubIndexKey<String, RsExternCrateItem> =
            StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RsExternCrateReexportIndex")

        fun index(stub: RsExternCrateItemStub, sink: IndexSink) {
            val externCrateItem = stub.psi
            val isPublic = externCrateItem?.vis != null
            if (!isPublic) return
            sink.occurrence(KEY, externCrateItem.referenceName)
        }

        fun findReexports(project: Project, crateRoot: RsMod): List<RsExternCrateItem> {
            checkCommitIsNotInProgress(project)
            return CachedValuesManager.getCachedValue(crateRoot) {
                val targetName = crateRoot.containingCargoTarget?.normName
                val reexports = if (targetName != null) {
                    getElements(KEY, targetName, project, GlobalSearchScope.allScope(project))
                        .filter { externCrateItem -> externCrateItem.reference.resolve() == crateRoot }
                } else {
                    emptyList()
                }

                CachedValueProvider.Result.create(reexports, crateRoot.rustStructureOrAnyPsiModificationTracker)
            }
        }
    }
}
