package org.rust.lang.core.resolve.indexes

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.AbstractStubIndex
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.util.io.KeyDescriptor
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsStructOrEnumItemElement
import org.rust.lang.core.psi.impl.mixin.isAssocFn
import org.rust.lang.core.stubs.RsImplItemStub
import org.rust.lang.core.stubs.RsFileStub
import org.rust.lang.core.types.RustStructOrEnumTypeBase
import org.rust.lang.core.types.RustType
import org.rust.lang.core.types.RustTypeFingerprint
import org.rust.lang.core.types.util.resolvedType
import java.util.*


object RsImplIndex {

    fun findMethodsFor(target: RustType, project: Project): Sequence<RsFunction> =
        findMethodsAndAssociatedFunctionsFor(target, project)
            .filter { !it.isAssocFn }

    fun findMethodsAndAssociatedFunctionsFor(target: RustType, project: Project): Sequence<RsFunction> =
        findImplsFor(target, project)
            .flatMap { it.functionList.orEmpty().asSequence() }

    fun findImplsFor(target: RustType, project: Project): Sequence<RsImplItem> {
        val inherentImpls = if (target is RustStructOrEnumTypeBase)
            findInherentImplsForInternal(target.item)
        else
            emptySequence()

        return findNonInherentImplsForInternal(target, project)
            .filter {
                it.type?.resolvedType == target
            } + inherentImpls
    }

    private fun findInherentImplsForInternal(target: RsStructOrEnumItemElement): Sequence<RsImplItem> {
        val fingerprint = RustTypeFingerprint.create(target.resolvedType)
            ?: return emptySequence()

        val found = ArrayList<RsImplItem>()

        StubIndex
            .getInstance()
            .processElements(
                InherentImpls.KEY,
                fingerprint,
                target.project,
                GlobalSearchScope.allScope(target.project),
                RsImplItem::class.java,
                {
                    found.add(it)
                    true /* continue */
                })

        return found.asSequence()
            .filter { impl ->
                val ty = impl.type?.resolvedType
                ty is RustStructOrEnumTypeBase && ty.item == target
            }
    }

    private fun findNonInherentImplsForInternal(target: RustType, project: Project): Sequence<RsImplItem> {
        val fingerprint = RustTypeFingerprint.create(target)
            ?: return emptySequence()

        val found = arrayListOf<RsImplItem>()

        StubIndex
            .getInstance()
            .processElements(
                TraitImpls.KEY,
                fingerprint,
                project,
                GlobalSearchScope.allScope(project),
                RsImplItem::class.java,
                {
                    found.add(it)
                    true /* continue */
                })

        return found.asSequence()

    }


    class TraitImpls : AbstractStubIndex<RustTypeFingerprint, RsImplItem>() {
        override fun getVersion(): Int = RsFileStub.Type.stubVersion
        override fun getKey(): StubIndexKey<RustTypeFingerprint, RsImplItem> = KEY
        override fun getKeyDescriptor(): KeyDescriptor<RustTypeFingerprint> = RustTypeFingerprint.KeyDescriptor

        companion object {
            val KEY: StubIndexKey<RustTypeFingerprint, RsImplItem> =
                StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RustImplIndex.TraitImpls")

            fun index(stub: RsImplItemStub, sink: IndexSink) {
                val type = stub.psi.type ?: return
                val key = RustTypeFingerprint.create(type)
                if (stub.psi.traitRef != null && key != null) {
                    sink.occurrence(KEY, key)
                }
            }
        }
    }


    class InherentImpls : AbstractStubIndex<RustTypeFingerprint, RsImplItem>() {
        override fun getVersion(): Int = RsFileStub.Type.stubVersion
        override fun getKey(): StubIndexKey<RustTypeFingerprint, RsImplItem> = KEY
        override fun getKeyDescriptor(): KeyDescriptor<RustTypeFingerprint> = RustTypeFingerprint.KeyDescriptor

        companion object {
            val KEY: StubIndexKey<RustTypeFingerprint, RsImplItem> =
                StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RustImplIndex.InherentImpls")

            fun index(stub: RsImplItemStub, sink: IndexSink) {
                val type = stub.psi.type ?: return
                val key = RustTypeFingerprint.create(type)
                if (key != null) {
                    sink.occurrence(KEY, key)
                }
            }
        }
    }
}
