package org.rust.lang.core.resolve.indexes

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.AbstractStubIndex
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.KeyDescriptor
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.isAssocFn
import org.rust.lang.core.psi.ext.queryAttributes
import org.rust.lang.core.psi.ext.resolveToTrait
import org.rust.lang.core.resolve.isDerefTrait
import org.rust.lang.core.resolve.langAttribute
import org.rust.lang.core.stubs.RsFileStub
import org.rust.lang.core.stubs.RsImplItemStub
import org.rust.lang.core.types.RustType
import org.rust.lang.core.types.RustTypeFingerprint
import org.rust.lang.core.types.type
import java.util.concurrent.ConcurrentMap

private val RsImplItem.targetType: RustType? get() {
    return this.typeAliasList.find { it.name == "Target" }?.typeReference?.type
}

class RsImplIndex : AbstractStubIndex<RustTypeFingerprint, RsImplItem>() {
    override fun getVersion(): Int = RsFileStub.Type.stubVersion
    override fun getKey(): StubIndexKey<RustTypeFingerprint, RsImplItem> = KEY
    override fun getKeyDescriptor(): KeyDescriptor<RustTypeFingerprint> = RustTypeFingerprint.KeyDescriptor

    companion object {
        fun findMethodsFor(target: RustType, project: Project): Collection<RsFunction> =
            findMethodsAndAssociatedFunctionsFor(target, project)
                .filter { !it.isAssocFn }

        fun findMethodsAndAssociatedFunctionsFor(target: RustType, project: Project): Collection<RsFunction> =
            findImplsFor(target, project, true)
                .flatMap { it.allMethodsAndAssocFunctions }

        fun findImplsFor(
            target: RustType,
            project: Project,
            include_deref: Boolean = false,
            deref_hierarchic: Collection<RustType> = emptyList()
        ): Collection<RsImplItem> =
            project.implsCache
                .getOrPut(target) { doFindImplsFor(target, project, deref_hierarchic) }
                .filter { include_deref || it.typeReference?.type?.canUnifyWith(target, project) ?: false }

        private fun doFindImplsFor(
            target: RustType,
            project: Project,
            deref_hierarchic: Collection<RustType>
        ): Collection<RsImplItem> {
            val fingerprint = RustTypeFingerprint.create(target)
                ?: return emptyList()

            val collection = StubIndex.getElements(
                KEY,
                fingerprint,
                project,
                GlobalSearchScope.allScope(project),
                RsImplItem::class.java
            ).filter { impl ->
                val ty = impl.typeReference?.type
                // Addition class check is a temporal solution to filter impls for type parameter
                // with the same name
                // struct S; impl<S: Tr1> Tr2 for S {}
                ty != null && ty.javaClass == target.javaClass && ty.canUnifyWith(target, project)
            }
            val derefTargetType = collection.find(RsImplItem::isDerefTrait)?.targetType
            val derefImpls = if (derefTargetType != null && !deref_hierarchic.any { it == derefTargetType })
                findImplsFor(derefTargetType, project, true, deref_hierarchic + listOf(derefTargetType))
            else
                emptyList()

            return collection + derefImpls.filter { !collection.contains(it) }
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

private val RsImplItem.allMethodsAndAssocFunctions: Collection<RsFunction> get() {
    val directlyImplemented = functionList.map { it.name }.toSet()
    val defaulted = traitRef?.resolveToTrait?.functionList.orEmpty().asSequence().filter {
        it.name !in directlyImplemented
    }

    return functionList + defaulted
}

private val Project.implsCache: ConcurrentMap<RustType, Collection<RsImplItem>>
    get() = CachedValuesManager.getManager(this)
        .getCachedValue(this, {
            CachedValueProvider.Result.create(
                ContainerUtil.newConcurrentMap<RustType, Collection<RsImplItem>>(),
                PsiModificationTracker.MODIFICATION_COUNT
            )
        })
