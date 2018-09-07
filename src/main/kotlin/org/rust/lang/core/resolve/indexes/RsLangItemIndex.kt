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
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.RsItemElement
import org.rust.lang.core.psi.ext.containingCargoPackage
import org.rust.lang.core.psi.ext.queryAttributes
import org.rust.lang.core.stubs.RsFileStub
import org.rust.openapiext.getElements

class RsLangItemIndex : AbstractStubIndex<String, RsItemElement>() {
    override fun getVersion(): Int = RsFileStub.Type.stubVersion
    override fun getKey(): StubIndexKey<String, RsItemElement> = KEY
    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    companion object {
        fun findLangItem(project: Project, langAttribute: String, crateName: String = AutoInjectedCrates.CORE): RsTraitItem? {
            val elements = getElements(KEY, langAttribute, project, GlobalSearchScope.allScope(project))
            return if (elements.size < 2) {
                elements.firstOrNull() as? RsTraitItem
            } else {
                elements.find { it.containingCargoPackage?.normName == crateName } as? RsTraitItem
            }
        }

        fun findBoxItem(project: Project): RsStructItem? {
            val elements = getElements(KEY, "owned_box", project, GlobalSearchScope.allScope(project))
            return if (elements.size < 2) {
                elements.firstOrNull() as? RsStructItem
            } else {
                elements.find { it.containingCargoPackage?.normName == "alloc" } as? RsStructItem
            }
        }

        fun index(psi: RsItemElement, sink: IndexSink) {
            val key = psi.queryAttributes.langAttribute
            if (key != null) {
                sink.occurrence(KEY, key)
            }
        }

        private val KEY: StubIndexKey<String, RsItemElement> =
            StubIndexKey.createIndexKey("org.rust.lang.core.resolve.indexes.RsLangItemIndex")
    }
}
