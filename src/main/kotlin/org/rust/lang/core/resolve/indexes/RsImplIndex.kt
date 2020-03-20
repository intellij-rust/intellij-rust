/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.indexes

import com.intellij.openapi.project.Project
import com.intellij.psi.stubs.AbstractStubIndex
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.util.io.KeyDescriptor
import org.rust.ide.search.RsWithMacrosProjectScope
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.core.resolve.RsCachedImplItem
import org.rust.lang.core.resolve.RsProcessor
import org.rust.lang.core.stubs.RsFileStub
import org.rust.lang.core.stubs.RsImplItemStub
import org.rust.lang.core.types.TyFingerprint
import org.rust.openapiext.getElements

class RsImplIndex : AbstractStubIndex<TyFingerprint, RsImplItem>() {
    override fun getVersion(): Int = RsFileStub.Type.stubVersion
    override fun getKey(): StubIndexKey<TyFingerprint, RsImplItem> = KEY
    override fun getKeyDescriptor(): KeyDescriptor<TyFingerprint> = TyFingerprint.KeyDescriptor

    companion object {
        /** return impls for generic type `impl<T> Trait for T {}` */
        fun findFreeImpls(project: Project, processor: RsProcessor<RsCachedImplItem>): Boolean {
            return findPotentialImpls(project, TyFingerprint.TYPE_PARAMETER_FINGERPRINT, processor)
        }

        /**
         * Note this method may return false positives
         * @see TyFingerprint
         */
        fun findPotentialImpls(
            project: Project,
            tyf: TyFingerprint,
            processor: RsProcessor<RsCachedImplItem>
        ): Boolean {
            val impls = getElements(KEY, tyf, project, RsWithMacrosProjectScope(project))

            // Note that `getElements` is intentionally used with intermediate collection instead of
            // `StubIndex.processElements` in order to simplify profiling
            return impls.any {
                val cachedImpl = RsCachedImplItem.forImpl(project, it)
                cachedImpl.isValid && processor(cachedImpl)
            }
        }

        fun index(stub: RsImplItemStub, sink: IndexSink) {
            val impl = stub.psi
            val typeRef = impl.typeReference ?: return
            TyFingerprint.create(typeRef, impl.typeParameters.mapNotNull { it.name })
                .forEach { sink.occurrence(KEY, it) }
        }

        private val KEY: StubIndexKey<TyFingerprint, RsImplItem> =
            StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RustImplIndex.TraitImpls")
    }
}
