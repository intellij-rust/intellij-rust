/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.indexes

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.AbstractStubIndex
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import org.rust.cargo.util.AutoInjectedCrates
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.stubs.RsFileStub
import org.rust.openapiext.checkCommitIsNotInProgress
import org.rust.openapiext.getElements
import org.rust.stdext.singleOrFilter

class RsLangItemIndex : AbstractStubIndex<String, RsItemElement>() {
    override fun getVersion(): Int = RsFileStub.Type.stubVersion
    override fun getKey(): StubIndexKey<String, RsItemElement> = KEY
    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    companion object {
        fun findLangItem(
            project: Project,
            langAttribute: String,
            crateName: String = AutoInjectedCrates.CORE
        ): RsNamedElement? {
            checkCommitIsNotInProgress(project)
            return getElements(KEY, langAttribute, project, GlobalSearchScope.allScope(project))
                .filterIsInstance<RsNamedElement>()
                .singleOrFilter { it.containingCrate.normName == crateName }
                .singleOrFilter { it.existsAfterExpansion }
                .firstOrNull()
        }

        fun index(psi: RsItemElement, sink: IndexSink) {
            for (key in psi.getTraversedRawAttributes().langAttributes) {
                sink.occurrence(KEY, key)
            }
        }

        private val KEY: StubIndexKey<String, RsItemElement> =
            StubIndexKey.createIndexKey("org.rust.lang.core.resolve.indexes.RsLangItemIndex")
    }
}
