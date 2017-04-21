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
import org.rust.lang.core.psi.ext.isAssocFn
import org.rust.lang.core.psi.ext.resolveToTrait
import org.rust.lang.core.stubs.RsFileStub
import org.rust.lang.core.stubs.RsImplItemStub
import org.rust.lang.core.types.RustType
import org.rust.lang.core.types.RustTypeFingerprint
import org.rust.lang.core.types.type
import org.rust.lang.core.types.types.RustSliceType
import org.rust.lang.core.types.types.RustStructOrEnumTypeBase
import org.rust.lang.core.types.types.RustTypeParameterType
import org.rust.lang.core.types.types.RustUnknownType

class RsImplIndex : AbstractStubIndex<RustTypeFingerprint, RsImplItem>() {
    override fun getVersion(): Int = RsFileStub.Type.stubVersion
    override fun getKey(): StubIndexKey<RustTypeFingerprint, RsImplItem> = KEY
    override fun getKeyDescriptor(): KeyDescriptor<RustTypeFingerprint> = RustTypeFingerprint.KeyDescriptor

    companion object {
        fun findMethodsFor(target: RustType, project: Project): Sequence<RsFunction> =
            findMethodsAndAssociatedFunctionsFor(target, project)
                .filter { !it.isAssocFn }

        fun findMethodsAndAssociatedFunctionsFor(target: RustType, project: Project): Sequence<RsFunction> =
            findImplsFor(target, project)
                .flatMap { it.allMethodsAndAssocFunctions }

        fun findImplsFor(target: RustType, project: Project): Sequence<RsImplItem> {
            val fingerprint = RustTypeFingerprint.create(target)
                ?: return emptySequence()

            val elements = StubIndex.getElements(
                KEY,
                fingerprint,
                project,
                GlobalSearchScope.allScope(project),
                RsImplItem::class.java
            ).asSequence()
            return when {
                else -> elements.filter {
                    canUnify(target, it.typeReference?.type ?: RustUnknownType)
                }
            }
        }

        fun index(stub: RsImplItemStub, sink: IndexSink) {
            val type = stub.psi.typeReference ?: return
            val key = RustTypeFingerprint.create(type)
            if (key != null) {
                sink.occurrence(KEY, key)
            }
        }

        private val KEY: StubIndexKey<RustTypeFingerprint, RsImplItem> =
            StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RustImplIndex.TraitImpls")
    }
}

private val RsImplItem.allMethodsAndAssocFunctions: Sequence<RsFunction> get() {
    val directlyImplemented = functionList.map { it.name }.toSet()
    val defaulted = traitRef?.resolveToTrait?.functionList.orEmpty().asSequence().filter {
        it.name !in directlyImplemented
    }

    return functionList.asSequence() + defaulted
}

private fun canUnify(t1: RustType, t2: RustType): Boolean {
    if (t1 == t2) return true
    if (t1 is RustTypeParameterType || t2 is RustTypeParameterType) return true
    if (t1 is RustStructOrEnumTypeBase && t2 is RustStructOrEnumTypeBase) {
        return t1.item == t2.item && t1.typeArguments.zip(t2.typeArguments).all { (a, b) -> canUnify(a, b) }
    }
    if (t1 is RustSliceType && t2 is RustSliceType) {
        return canUnify(t1.elementType, t2.elementType)
    }
    return false
}
