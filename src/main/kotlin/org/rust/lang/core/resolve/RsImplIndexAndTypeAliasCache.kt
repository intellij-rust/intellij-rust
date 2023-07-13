/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.containers.ContainerUtil
import org.rust.lang.core.psi.RustStructureChangeListener
import org.rust.lang.core.psi.rustPsiManager
import org.rust.lang.core.resolve.indexes.RsAliasIndex
import org.rust.lang.core.resolve.indexes.RsImplIndex
import org.rust.lang.core.types.TyFingerprint
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicReference

@Service
class RsImplIndexAndTypeAliasCache(private val project: Project) : Disposable {
    // strong key -> soft value maps
    private val _implIndexCache: AtomicReference<ConcurrentMap<TyFingerprint, List<RsCachedImplItem>>?> = AtomicReference(null)
    private val _typeAliasIndexCache: AtomicReference<ConcurrentMap<TyFingerprint, List<RsCachedTypeAlias>>?> = AtomicReference(null)

    private val implIndexCache: ConcurrentMap<TyFingerprint, List<RsCachedImplItem>>
        get() = _implIndexCache.getOrCreateMap()

    private val typeAliasIndexCache: ConcurrentMap<TyFingerprint, List<RsCachedTypeAlias>>
        get() = _typeAliasIndexCache.getOrCreateMap()

    /**
     * This map is actually used is a [Set] (the value is always [placeholder]).
     * The only purpose of this set is holding links to [PsiFile]s, so retain them in memory.
     * Without this set [PsiFile]s are retained only by [java.lang.ref.WeakReference], hence they are
     * quickly collected by GC and then further index lookups work slower.
     *
     * Note: keys in this map are referenced via [java.lang.ref.SoftReference], so they're also
     * can be collected by GC, hence there isn't a memory leak
     */
    private val usedPsiFiles: ConcurrentMap<PsiFile, Any> = ContainerUtil.createConcurrentSoftMap()
    private val placeholder = Any()

    init {
        val rustPsiManager = project.rustPsiManager
        val connection = project.messageBus.connect(this)
        rustPsiManager.subscribeRustStructureChange(connection, object : RustStructureChangeListener {
            override fun rustStructureChanged(file: PsiFile?, changedElement: PsiElement?) {
                _implIndexCache.getAndSet(null)
                _typeAliasIndexCache.getAndSet(null)
            }
        })
    }

    fun findPotentialImpls(tyf: TyFingerprint): List<RsCachedImplItem> {
        return implIndexCache.getOrPut(tyf) {
            RsImplIndex.findPotentialImpls(project, tyf).filter {
                retainPsi(it.impl.containingFile)
                it.isValid
            }
        }
    }

    fun findPotentialAliases(tyf: TyFingerprint): List<RsCachedTypeAlias> {
        return typeAliasIndexCache.getOrPut(tyf) {
            RsAliasIndex.findPotentialAliases(project, tyf)
        }
    }

    private fun retainPsi(containingFile: PsiFile) {
        usedPsiFiles[containingFile] = placeholder
    }

    override fun dispose() {}

    companion object {
        fun getInstance(project: Project): RsImplIndexAndTypeAliasCache =
            project.service()

        @JvmStatic
        private fun <T : Any> AtomicReference<ConcurrentMap<TyFingerprint, T>?>.getOrCreateMap(): ConcurrentMap<TyFingerprint, T> {
            while (true) {
                get()?.let { return it }
                val map = ContainerUtil.createConcurrentSoftValueMap<TyFingerprint, T>()
                if (compareAndSet(null, map)) return map
            }
        }
    }
}
