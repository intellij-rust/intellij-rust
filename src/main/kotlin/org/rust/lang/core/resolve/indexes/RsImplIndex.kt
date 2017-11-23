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
import org.rust.lang.core.psi.RsBaseType
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.ext.name
import org.rust.lang.core.psi.ext.typeElement
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.core.stubs.RsFileStub
import org.rust.lang.core.stubs.RsImplItemStub
import org.rust.lang.core.types.TyFingerprint
import org.rust.lang.core.types.ty.Ty
import org.rust.openapiext.getElements

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

        private val KEY: StubIndexKey<TyFingerprint, RsImplItem> =
            StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RustImplIndex.TraitImpls")
    }
}
