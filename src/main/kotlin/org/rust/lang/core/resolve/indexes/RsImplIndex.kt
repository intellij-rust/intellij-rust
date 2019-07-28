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
import org.rust.ide.search.RsWithMacrosProjectScope
import org.rust.lang.core.macros.macroExpansionManager
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.core.resolve.RsCachedImplItem
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
        fun findPotentialImpls(project: Project, target: Ty): Sequence<RsCachedImplItem> {
            project.macroExpansionManager.ensureUpToDate()
            val impls = run {
                val fingerprint = TyFingerprint.create(target)
                    ?: return@run emptyList<RsImplItem>()
                getElements(KEY, fingerprint, project, RsWithMacrosProjectScope(project))
            }
            val freeImpls = getElements(KEY, TyFingerprint.TYPE_PARAMETER_FINGERPRINT, project, RsWithMacrosProjectScope(project))
            // filter dangling (not attached to some crate) rust files, e.g. tests, generated source
            return (impls.asSequence() + freeImpls.asSequence())
                .map { RsCachedImplItem.forImpl(project, it) }
                .filter { it.crateRoot != null }
        }

        /** return impls for generic type `impl<T> Trait for T {}` */
        fun findFreeImpls(project: Project): Sequence<RsCachedImplItem> {
            val freeImpls = getElements(KEY, TyFingerprint.TYPE_PARAMETER_FINGERPRINT, project, GlobalSearchScope.allScope(project))
            // filter dangling (not attached to some crate) rust files, e.g. tests, generated source
            return freeImpls.asSequence()
                .map { RsCachedImplItem.forImpl(project, it) }
                .filter { it.crateRoot != null }
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
