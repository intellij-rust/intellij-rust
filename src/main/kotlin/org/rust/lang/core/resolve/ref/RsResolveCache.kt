/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("DEPRECATION")

package org.rust.lang.core.resolve.ref

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapiext.Testmark
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.AnyPsiChangeListener
import com.intellij.psi.impl.PsiManagerImpl.ANY_PSI_CHANGE_TOPIC
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.containers.ConcurrentWeakKeySoftValueHashMap
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.messages.MessageBus
import org.rust.lang.core.macros.macroExpansionManager
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsModificationTrackerOwner
import org.rust.lang.core.psi.ext.RsReferenceElement
import org.rust.lang.core.psi.ext.ancestors
import org.rust.lang.core.psi.ext.findModificationTrackerOwner
import java.lang.ref.ReferenceQueue
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicReference

/**
 * The implementation is inspired by Intellij platform's [com.intellij.psi.impl.source.resolve.ResolveCache].
 * The main difference from the platform one: we invalidate the cache depends on [ResolveCacheDependency], when
 * platform cache invalidates on any PSI change.
 *
 * See [RsPsiManager.rustStructureModificationTracker].
 */
class RsResolveCache(messageBus: MessageBus) {
    /** The cache is cleared on [RsPsiManager.rustStructureModificationTracker] increment */
    private val _rustStructureDependentCache: AtomicReference<ConcurrentMap<PsiElement, Any?>?> = AtomicReference(null)
    /** The cache is cleared on [ANY_PSI_CHANGE_TOPIC] event */
    private val _anyPsiChangeDependentCache: AtomicReference<ConcurrentMap<PsiElement, Any?>?> = AtomicReference(null)
    private val _macroCache: AtomicReference<ConcurrentMap<PsiElement, Any?>?> = AtomicReference(null)
    private val guard = RecursionManager.createGuard<PsiElement>("RsResolveCache")

    private val rustStructureDependentCache: ConcurrentMap<PsiElement, Any?>
        get() = _rustStructureDependentCache.getOrCreateMap()

    private val anyPsiChangeDependentCache: ConcurrentMap<PsiElement, Any?>
        get() = _anyPsiChangeDependentCache.getOrCreateMap()

    private val macroCache: ConcurrentMap<PsiElement, Any?>
        get() = _macroCache.getOrCreateMap()

    init {
        val connection = messageBus.connect()
        connection.subscribe(RUST_STRUCTURE_CHANGE_TOPIC, object : RustStructureChangeListener {
            override fun rustStructureChanged(file: PsiFile?, changedElement: PsiElement?) =
                onRustStructureChanged(file)
        })
        connection.subscribe(ANY_PSI_CHANGE_TOPIC, object : AnyPsiChangeListener {
            override fun afterPsiChanged(isPhysical: Boolean) {
                _anyPsiChangeDependentCache.set(null)
            }

            override fun beforePsiChanged(isPhysical: Boolean) {}
        })
        connection.subscribe(RUST_PSI_CHANGE_TOPIC, object : RustPsiChangeListener {
            override fun rustPsiChanged(file: PsiFile, element: PsiElement, isStructureModification: Boolean) =
                onRustPsiChanged(element)
        })
    }

    /**
     * Retrieve a cached value by [key] or compute a new value by [resolver].
     * Internally recursion-guarded by [key].
     *
     * Expected resolve results: [PsiElement], [ResolveResult] or a [List]/[Array] of [ResolveResult]
     */
    @Suppress("UNCHECKED_CAST")
    fun <K : PsiElement, V> resolveWithCaching(key: K, dep: ResolveCacheDependency, resolver: (K) -> V): V? {
        ProgressManager.checkCanceled()
        val refinedDep = refineDependency(key, dep)
        val map = getCacheFor(key, refinedDep)
        return map[key] as V? ?: run {
            val stamp = RecursionManager.markStack()
            val result = guard.doPreventingRecursion(key, true) { resolver(key) }
            ensureValidResult(result)

            if (stamp.mayCacheNow()) {
                cache(map, key, result)
            }
            result
        }
    }

    fun getCached(key: PsiElement, dep: ResolveCacheDependency): Any? {
        return getCacheFor(key, refineDependency(key, dep))[key]
    }

    private fun refineDependency(key: PsiElement, dep: ResolveCacheDependency): ResolveCacheDependency =
        when (key.containingFile.virtualFile) {
            // If virtualFile is null then event system is not enabled for this PSI file (see
            // PsiFileImpl.getVirtualFile) and we can't track PSI modifications, so depend on
            // any change. This is a case of completion, for example
            null -> ResolveCacheDependency.ANY_PSI_CHANGE
            // The case of injected language. Injected PSI don't have it's own event system, so can only
            // handle evens from outer PSI. For example, Rust language is injected to Kotlin's string
            // literal. If a user change the literal, we can only be notified that the literal is changed.
            // So we have to invalidate caches for injected PSI on any PSI change
            is VirtualFileWindow -> ResolveCacheDependency.ANY_PSI_CHANGE
            else -> dep
        }

    private fun getCacheFor(element: PsiElement, dep: ResolveCacheDependency): ConcurrentMap<PsiElement, Any?> {
        return when (dep) {
            ResolveCacheDependency.LOCAL, ResolveCacheDependency.LOCAL_AND_RUST_STRUCTURE -> {
                val owner = element.findModificationTrackerOwner(strict = false)
                return if (owner != null) {
                    if (dep == ResolveCacheDependency.LOCAL) {
                        CachedValuesManager.getCachedValue(owner, LOCAL_CACHE_KEY) {
                            CachedValueProvider.Result.create(
                                createWeakMap(),
                                owner.modificationTracker
                            )
                        }

                    } else {
                        CachedValuesManager.getCachedValue(owner, LOCAL_CACHE_KEY2) {
                            CachedValueProvider.Result.create(
                                createWeakMap(),
                                owner.project.rustStructureModificationTracker,
                                owner.modificationTracker
                            )
                        }
                    }
                } else {
                    rustStructureDependentCache
                }

            }
            ResolveCacheDependency.RUST_STRUCTURE -> rustStructureDependentCache
            ResolveCacheDependency.ANY_PSI_CHANGE -> anyPsiChangeDependentCache
            ResolveCacheDependency.MACRO -> macroCache
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <K : PsiElement, V> cache(map: ConcurrentMap<PsiElement, Any?>, element: K, result: V?) {
        // optimization: less contention
        val cached = map[element] as V?
        if (cached !== null && cached === result) return
        map[element] = result ?: NULL_RESULT as V
    }

    private fun onRustStructureChanged(file: PsiFile?) {
        Testmarks.rustStructureDependentCacheCleared.hit()
        _rustStructureDependentCache.set(null)
        if (file != null && _macroCache.get() != null) {
            val viFile = file.virtualFile
            if (viFile != null && !file.project.macroExpansionManager.isExpansionFile(viFile)) {
                // Invalidate cache only on changes OUTSIDE of expansion files
                _macroCache.set(null)
            }
        }
    }

    private fun onRustPsiChanged(element: PsiElement) {
        // 1. The global resolve cache invalidates only on rust structure changes.
        // 2. If some reference element is located inside RsModificationTrackerOwner,
        //    the global cache will not be invalidated on its change
        // 3. PSI uses default identity-based equals/hashCode
        // 4. Intellij does incremental updates of a mutable PSI tree.
        //
        // It means that some reference element may be changed and then should be
        // re-resolved to another target. But if we use the global cache, we will
        // retrieve its previous resolve result from the cache. So if some reference
        // element is changed, we should remove it (and its ancestors) from the
        // global cache

        val referenceElement = element.parent as? RsReferenceElement ?: return
        val referenceNameElement = referenceElement.referenceNameElement ?: return
        if (referenceNameElement == element) {
            Testmarks.removeChangedElement.hit()
            referenceElement.ancestors.filter { it is RsReferenceElement }.forEach {
                rustStructureDependentCache.remove(it)
            }
        }
    }

    fun endExpandingMacros() {
        _macroCache.set(null)
    }

    companion object {
        fun getInstance(project: Project): RsResolveCache =
            ServiceManager.getService(project, RsResolveCache::class.java)
    }

    object Testmarks {
        val rustStructureDependentCacheCleared = Testmark("cacheCleared")
        val removeChangedElement = Testmark("removeChangedElement")
    }
}

enum class ResolveCacheDependency {
    /**
     * Depends on the nearest [RsModificationTrackerOwner] and falls back to
     * [RsPsiManager.rustStructureModificationTracker] if the tracker owner is not found.
     *
     * See [findModificationTrackerOwner]
     */
    LOCAL,

    /**
     * Depends on [RsPsiManager.rustStructureModificationTracker]
     */
    RUST_STRUCTURE,

    /**
     * Depends on both [LOCAL] and [RUST_STRUCTURE]. It is not the same as "any PSI change", because,
     * for example, local changes from other functions will not invalidate the value
     */
    LOCAL_AND_RUST_STRUCTURE,

    /**
     * Depends on [com.intellij.psi.util.PsiModificationTracker.MODIFICATION_COUNT]. I.e. depends on
     * any PSI change, not only in rust files
     */
    ANY_PSI_CHANGE,

    /**
     * Very specific to the new macro expansion engine. The cache is valid during macro expansion
     * process (see [org.rust.lang.core.macros.MacroExpansionTaskBase]) and invalidates at the end
     * of expansion process (see [RsResolveCache.endExpandingMacros]).
     *
     * During macro resolve we can multiple times change expansion files and so increment
     * [RsPsiManager.rustStructureModificationTracker]. But these changes can't affect the values
     * that stored in the cache previously because of specific order of macro expansion we use.
     * This dependency should be used only in macro expansion task and only when we call resolve
     * in the correct order.
     *
     * @see RsMacroPathReferenceImpl.resolveInBatchMode
     */
    MACRO,
}

private fun AtomicReference<ConcurrentMap<PsiElement, Any?>?>.getOrCreateMap(): ConcurrentMap<PsiElement, Any?> {
    while (true) {
        get()?.let { return it }
        val map = createWeakMap<PsiElement, Any?>()
        if (compareAndSet(null, map)) return map
    }
}

private fun <K, V> createWeakMap(): ConcurrentMap<K, V> {
    return object : ConcurrentWeakKeySoftValueHashMap<K, V>(
        100,
        0.75f,
        Runtime.getRuntime().availableProcessors(),
        ContainerUtil.canonicalStrategy()
    ) {
        override fun createValueReference(
            value: V,
            queue: ReferenceQueue<in V>
        ): ConcurrentWeakKeySoftValueHashMap.ValueReference<K, V> {
            val isTrivialValue = value === NULL_RESULT ||
                value is Array<*> && value.size == 0 ||
                value is List<*> && value.size == 0
            return if (isTrivialValue) {
                createStrongReference(value)
            } else {
                super.createValueReference(value, queue)
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun get(key: K): V? {
            val v = super.get(key)
            return if (v === NULL_RESULT) null else v
        }
    }
}

private class StrongValueReference<K, V>(
    private val value: V
) : ConcurrentWeakKeySoftValueHashMap.ValueReference<K, V> {
    override fun getKeyReference(): ConcurrentWeakKeySoftValueHashMap.KeyReference<K, V> {
        // will never GC so this method will never be called so no implementation is necessary
        throw UnsupportedOperationException()
    }

    override fun get(): V = value
}

@Suppress("UNCHECKED_CAST")
private fun <K, V> createStrongReference(value: V): StrongValueReference<K, V> {
    return when {
        value === NULL_RESULT -> NULL_VALUE_REFERENCE as StrongValueReference<K, V>
        value === ResolveResult.EMPTY_ARRAY -> EMPTY_RESOLVE_RESULT as StrongValueReference<K, V>
        value is List<*> && value.size == 0 -> EMPTY_LIST as StrongValueReference<K, V>
        else -> StrongValueReference(value)
    }
}

private val NULL_RESULT = Any()
private val NULL_VALUE_REFERENCE = StrongValueReference<Any, Any>(NULL_RESULT)
private val EMPTY_RESOLVE_RESULT = StrongValueReference<Any, Array<ResolveResult>>(ResolveResult.EMPTY_ARRAY)
private val EMPTY_LIST = StrongValueReference<Any, List<Any>>(emptyList())

private val LOCAL_CACHE_KEY: Key<CachedValue<ConcurrentMap<PsiElement, Any?>>> = Key.create("LOCAL_CACHE_KEY")
private val LOCAL_CACHE_KEY2: Key<CachedValue<ConcurrentMap<PsiElement, Any?>>> = Key.create("LOCAL_CACHE_KEY2")

private fun ensureValidResult(result: Any?): Unit = when (result) {
    is ResolveResult -> ensureValidPsi(result)
    is Array<*> -> ensureValidResults(result)
    is List<*> -> ensureValidResults(result)
    is PsiElement -> PsiUtilCore.ensureValid(result)
    else -> Unit
}

private fun ensureValidResults(result: Array<*>) =
    result.forEach { ensureValidResult(it) }

private fun ensureValidResults(result: List<*>) =
    result.forEach { ensureValidResult(it) }

private fun ensureValidPsi(resolveResult: ResolveResult) {
    val element = resolveResult.element
    if (element != null) {
        PsiUtilCore.ensureValid(element)
    }
}
