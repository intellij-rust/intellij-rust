package org.rust.lang.core.resolve.indexes

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.*
import com.intellij.util.io.KeyDescriptor
import org.rust.lang.core.psi.RustFunctionElement
import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.psi.RustStructOrEnumItemElement
import org.rust.lang.core.psi.impl.mixin.isStatic
import org.rust.lang.core.stubs.RustFileStub
import org.rust.lang.core.stubs.RustImplItemElementStub
import org.rust.lang.core.types.RustStructOrEnumTypeBase
import org.rust.lang.core.types.RustType
import org.rust.lang.core.types.RustTypeFingerprint
import org.rust.lang.core.types.util.resolvedType
import java.util.*


object RustImplIndex {

    fun findNonStaticMethodsFor(target: RustType, project: Project): Sequence<RustFunctionElement> =
        findMethodsFor(target, project)
            .filter { !it.isStatic }

    fun findMethodsFor(target: RustType, project: Project): Sequence<RustFunctionElement> =
        findImplsFor(target, project)
            .flatMap { it.functionList.orEmpty().asSequence() }

    fun findImplsFor(target: RustType, project: Project): Sequence<RustImplItemElement> {
        val inherentImpls = if (target is RustStructOrEnumTypeBase)
            findInherentImplsForInternal(target.item)
        else
            emptySequence()

        return findNonInherentImplsForInternal(target, project)
            .filter {
                it.type?.resolvedType == target
            } + inherentImpls
    }

    private fun findInherentImplsForInternal(target: RustStructOrEnumItemElement): Sequence<RustImplItemElement> {
        val fingerprint = RustTypeFingerprint.create(target.resolvedType)
            ?: return emptySequence()

        val found = ArrayList<RustImplItemElement>()

        StubIndex
            .getInstance()
            .processElements(
                InherentImpls.KEY,
                fingerprint,
                target.project,
                GlobalSearchScope.allScope(target.project),
                RustImplItemElement::class.java,
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

    private fun findNonInherentImplsForInternal(target: RustType, project: Project): Sequence<RustImplItemElement> {
        val fingerprint = RustTypeFingerprint.create(target)
            ?: return emptySequence()

        val found = arrayListOf<RustImplItemElement>()

        StubIndex
            .getInstance()
            .processElements(
                TraitImpls.KEY,
                fingerprint,
                project,
                GlobalSearchScope.allScope(project),
                RustImplItemElement::class.java,
                {
                    found.add(it)
                    true /* continue */
                })

        return found.asSequence()

    }


    class TraitImpls : AbstractStubIndex<RustTypeFingerprint, RustImplItemElement>() {
        override fun getVersion(): Int = RustFileStub.Type.stubVersion
        override fun getKey(): StubIndexKey<RustTypeFingerprint, RustImplItemElement> = KEY
        override fun getKeyDescriptor(): KeyDescriptor<RustTypeFingerprint> = RustTypeFingerprint.KeyDescriptor

        companion object {
            val KEY: StubIndexKey<RustTypeFingerprint, RustImplItemElement> =
                StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RustImplIndex.TraitImpls")

            fun index(stub: RustImplItemElementStub, sink: IndexSink) {
                val type = stub.psi.type ?: return
                val key = RustTypeFingerprint.create(type)
                if (stub.traitRef != null && key != null) {
                    sink.occurrence(KEY, key)
                }
            }
        }
    }


    class InherentImpls : AbstractStubIndex<RustTypeFingerprint, RustImplItemElement>() {
        override fun getVersion(): Int = RustFileStub.Type.stubVersion
        override fun getKey(): StubIndexKey<RustTypeFingerprint, RustImplItemElement> = KEY
        override fun getKeyDescriptor(): KeyDescriptor<RustTypeFingerprint> = RustTypeFingerprint.KeyDescriptor

        companion object {
            val KEY: StubIndexKey<RustTypeFingerprint, RustImplItemElement> =
                StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RustImplIndex.InherentImpls")

            fun index(stub: RustImplItemElementStub, sink: IndexSink) {
                val type = stub.psi.type ?: return
                val key = RustTypeFingerprint.create(type)
                if (key != null) {
                    sink.occurrence(KEY, key)
                }
            }
        }
    }
}
