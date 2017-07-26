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
import org.rust.lang.core.psi.RsImplItem
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
        fun findImpls(project: Project, target: Ty): Collection<RsImplItem> {
            val fingerprint = TyFingerprint.create(target)
                ?: return emptyList()

            return getElements(KEY, fingerprint, project, GlobalSearchScope.allScope(project))
                .filter { impl ->
                    val ty = impl.typeReference?.type
                    // Addition class check is a temporal solution to filter impls for type parameter
                    // with the same name
                    // struct S; impl<S: Tr1> Tr2 for S {}
                    ty != null && ty.javaClass == target.javaClass && ty.canUnifyWith(target, project)
                }
        }

        fun index(stub: RsImplItemStub, sink: IndexSink) {
            val type = stub.psi.typeReference ?: return
            val key = TyFingerprint.create(type)
            if (key != null) {
                sink.occurrence(KEY, key)
            }
        }

        private val KEY: StubIndexKey<TyFingerprint, RsImplItem> =
            StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RustImplIndex.TraitImpls")
    }
}
