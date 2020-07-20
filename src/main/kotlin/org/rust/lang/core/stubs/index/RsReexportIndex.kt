/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.stubs.index

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.AbstractStubIndex
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.util.io.KeyDescriptor
import org.rust.lang.core.psi.RsUseItem
import org.rust.lang.core.psi.RsUseSpeck
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.itemName
import org.rust.lang.core.psi.ext.nameInScope
import org.rust.lang.core.psi.ext.pathOrQualifier
import org.rust.lang.core.stubs.RsFileStub
import org.rust.lang.core.stubs.RsUseSpeckStub
import org.rust.openapiext.checkCommitIsNotInProgress
import org.rust.openapiext.getElements
import java.io.DataInput
import java.io.DataOutput

class RsReexportIndex : AbstractStubIndex<ReexportKey, RsUseSpeck>() {
    override fun getVersion(): Int = RsFileStub.Type.stubVersion
    override fun getKey(): StubIndexKey<ReexportKey, RsUseSpeck> = KEY
    override fun getKeyDescriptor(): KeyDescriptor<ReexportKey> = ReexportKey.KeyDescriptor

    companion object {
        val KEY: StubIndexKey<ReexportKey, RsUseSpeck> =
            StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RsReexportIndex")

        fun index(stub: RsUseSpeckStub, sink: IndexSink) {
            val useSpeck = stub.psi
            val isPublic = useSpeck.ancestorStrict<RsUseItem>()?.vis != null
            if (!isPublic) return
            val (originalName, producedName) = if (stub.isStarImport) {
                useSpeck.pathOrQualifier?.referenceName to null
            } else {
                useSpeck.itemName(withAlias = false) to useSpeck.nameInScope
            }

            originalName?.let { sink.occurrence(KEY, ReexportKey.OriginalNameKey(it)) }
            producedName?.let { sink.occurrence(KEY, ReexportKey.ProducedNameKey(it)) }
        }

        fun findReexportsByProducedName(
            project: Project,
            target: String,
            scope: GlobalSearchScope = GlobalSearchScope.allScope(project)
        ): Collection<RsUseSpeck> = findReexportsByName(project, ReexportKey.ProducedNameKey(target), scope)

        fun findReexportsByOriginalName(
            project: Project,
            target: String,
            scope: GlobalSearchScope = GlobalSearchScope.allScope(project)
        ): Collection<RsUseSpeck> = findReexportsByName(project, ReexportKey.OriginalNameKey(target), scope)

        private fun findReexportsByName(
            project: Project,
            key: ReexportKey,
            scope: GlobalSearchScope
        ): Collection<RsUseSpeck> {
            checkCommitIsNotInProgress(project)
            return getElements(KEY, key, project, scope)
        }
    }
}

sealed class ReexportKey {

    abstract val name: String

    /**
     * For `use foo as bar` use item, [name] is `foo`
     */
    data class OriginalNameKey(override val name: String) : ReexportKey()

    /**
     * For `use foo as bar` use item, [name] is `bar`
     */
    data class ProducedNameKey(override val name: String) : ReexportKey()

    object KeyDescriptor : com.intellij.util.io.KeyDescriptor<ReexportKey> {
        override fun save(out: DataOutput, value: ReexportKey) {
            out.writeBoolean(value is OriginalNameKey)
            out.writeUTF(value.name)
        }

        override fun read(`in`: DataInput): ReexportKey {
            val isOriginalNameKey = `in`.readBoolean()
            val name = `in`.readUTF()
            return if (isOriginalNameKey) OriginalNameKey(name) else ProducedNameKey(name)
        }

        override fun getHashCode(value: ReexportKey): Int = value.hashCode()
        override fun isEqual(lhs: ReexportKey, rhs: ReexportKey): Boolean = lhs == rhs
    }
}
