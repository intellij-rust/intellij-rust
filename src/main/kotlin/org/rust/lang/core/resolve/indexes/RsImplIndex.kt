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
import com.intellij.util.io.KeyDescriptor
import org.rust.ide.utils.isNullOrEmpty
import org.rust.lang.core.psi.RsBaseType
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsTypeParameter
import org.rust.lang.core.psi.ext.name
import org.rust.lang.core.psi.ext.typeElement
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.core.stubs.RsFileStub
import org.rust.lang.core.stubs.RsImplItemStub
import org.rust.lang.core.types.TyFingerprint
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.type
import org.rust.lang.utils.getElements

class RsImplIndex : AbstractStubIndex<TyFingerprint, RsImplItem>() {
    override fun getVersion(): Int = RsFileStub.Type.stubVersion
    override fun getKey(): StubIndexKey<TyFingerprint, RsImplItem> = KEY
    override fun getKeyDescriptor(): KeyDescriptor<TyFingerprint> = TyFingerprint.KeyDescriptor

    companion object {
        /**
         * Note this method may return false positives
         * @see TyFingerprint
         */
        fun findPotentialImpls(project: Project, target: Ty): Collection<RsImplItem> {
            val fingerprint = TyFingerprint.create(target)
                ?: return emptyList()

            val impls = getElements(KEY, fingerprint, project, GlobalSearchScope.allScope(project))
                .filter { impl ->
                    val ty = impl.typeReference?.type
                    // Addition class check is a temporal solution to filter impls for type parameter
                    // with the same name
                    // struct S; impl<S: Tr1> Tr2 for S {}
                    ty != null && ty.javaClass == target.javaClass
                }
            val freeImpls = getElements(KEY, TyFingerprint.TYPE_PARAMETER_FINGERPRINT, project, GlobalSearchScope.allScope(project))
            return impls + freeImpls
        }

        fun index(stub: RsImplItemStub, sink: IndexSink) {
            val impl = stub.psi
            val typeRef = impl.typeReference ?: return
            val type = typeRef.typeElement

            val key = if (type is RsBaseType) {
                val typeParam = impl.typeParameters.find { it.name == type.name }
                if (typeParam != null) {
                    // At this moment we support only free trait impls without bounds
                    // to avoid stack overflow while resolving
                    if (hasBounds(typeParam, impl)) return
                    TyFingerprint.TYPE_PARAMETER_FINGERPRINT
                } else {
                    TyFingerprint.create(typeRef)
                }
            } else {
                TyFingerprint.create(typeRef)
            }

            if (key != null) {
                sink.occurrence(KEY, key)
            }
        }

        // We can't use `RsTypeParameter.bounds` because it uses `resolve`
        // and it can lead to `IndexNotReadyException` while indexing
        private fun hasBounds(typeParam: RsTypeParameter, impl: RsImplItem): Boolean {
            if (!typeParam.typeParamBounds?.polyboundList.isNullOrEmpty()) return true
            return impl.whereClause?.wherePredList.orEmpty()
                .any { (it.typeReference?.typeElement as? RsBaseType)?.name == typeParam.name }
        }

        private val KEY: StubIndexKey<TyFingerprint, RsImplItem> =
            StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RustImplIndex.TraitImpls")
    }
}
