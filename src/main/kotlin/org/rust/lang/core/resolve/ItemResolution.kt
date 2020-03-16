/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapiext.Testmark
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValue
import org.rust.cargo.util.AutoInjectedCrates.CORE
import org.rust.cargo.util.AutoInjectedCrates.STD
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.core.resolve.ref.advancedDeepResolve
import org.rust.openapiext.recursionGuard
import org.rust.stdext.intersects
import java.util.*

fun processItemOrEnumVariantDeclarations(
    scope: RsElement,
    ns: Set<Namespace>,
    processor: RsResolveProcessor,
    withPrivateImports: Boolean = false
): Boolean {
    when (scope) {
        // https://github.com/rust-lang/rfcs/blob/master/text/2338-type-alias-enum-variants.md
        is RsTypeAlias -> {
            val (item, subst) = (scope.typeReference?.typeElement as? RsBaseType)
                ?.path?.reference?.advancedDeepResolve() ?: return false
            if (item is RsEnumItem) {
                if (processAllWithSubst(item.variants, subst, processor)) return true
            }
        }
        is RsEnumItem -> {
            if (processAll(scope.variants, processor)) return true
        }
        is RsMod -> {
            val ipm = if (withPrivateImports) {
                ItemProcessingMode.WITH_PRIVATE_IMPORTS
            } else {
                ItemProcessingMode.WITHOUT_PRIVATE_IMPORTS
            }
            if (processItemDeclarations(scope, ns, processor, ipm)) return true
        }
    }

    return false
}

fun processItemDeclarations(
    scope: RsItemsOwner,
    ns: Set<Namespace>,
    originalProcessor: RsResolveProcessor,
    ipm: ItemProcessingMode
): Boolean {
    val withPrivateImports = ipm != ItemProcessingMode.WITHOUT_PRIVATE_IMPORTS

    val directlyDeclaredNames = HashSet<String>()
    val processor = { e: ScopeEntry ->
        directlyDeclaredNames += e.name
        originalProcessor(e)
    }

    val cachedItems = scope.expandedItemsCached

    loop@ for (item in cachedItems.rest) {
        when (item) {
            // Unit like structs are both types and values
            is RsStructItem -> {
                if (item.namespaces.intersects(ns) && processor(item)) return true
            }

            is RsModDeclItem -> if (Namespace.Types in ns) {
                val name = item.name ?: continue@loop
                val mod = item.reference.resolve() ?: continue@loop
                if (processor(name, mod)) return true
            }

            is RsEnumItem, is RsModItem, is RsTraitItem, is RsTypeAlias ->
                if (Namespace.Types in ns && processor(item as RsNamedElement)) return true

            is RsFunction, is RsConstant ->
                if (Namespace.Values in ns && processor(item as RsNamedElement)) return true

            is RsForeignModItem -> if (Namespace.Values in ns) {
                for (child in item.stubChildrenOfType<RsNamedElement>()) {
                    if (child is RsFunction || child is RsConstant) {
                        if (processor(child)) return true
                    }
                }
            }

            is RsExternCrateItem -> {
                if (processExternCrateItem(item, processor, withPrivateImports)) return true
            }
        }
    }

    val isEdition2018 = scope.isEdition2018
    for ((isPublic, path, name, isAtom) in cachedItems.namedImports) {
        if (!(isPublic || withPrivateImports)) continue

        if (isEdition2018 && isAtom) {
            // Use items like `use foo;` or `use foo::{self}` are meaningful on 2018 edition
            // only if `foo` is a crate, and it is `pub use` item. Otherwise,
            // we should ignore it or it breaks resolve of such `foo` in other places.
            ItemResolutionTestmarks.extraAtomUse.hit()
            if (!withPrivateImports) {
                val crate = findDependencyCrateByName(path, name)
                if (crate != null && processor(name, crate)) return true
            }
            continue
        }
        val pathReference = path.reference
        if (pathReference != null) {
            if (processMultiResolveWithNs(name, ns, pathReference, processor)) return true
        }
    }

    if (withPrivateImports && Namespace.Types in ns && scope is RsFile && !isEdition2018 && scope.isCrateRoot) {
        // Rust injects implicit `extern crate std` in every crate root module unless it is
        // a `#![no_std]` crate, in which case `extern crate core` is injected. However, if
        // there is a (unstable?) `#![no_core]` attribute, nothing is injected.
        //
        // https://doc.rust-lang.org/book/using-rust-without-the-standard-library.html
        // The stdlib lib itself is `#![no_std]`, and the core is `#![no_core]`
        when (scope.attributes) {
            RsFile.Attributes.NONE ->
                if (processor.lazy(STD) { scope.findDependencyCrateRoot(STD) }) return true

            RsFile.Attributes.NO_STD ->
                if (processor.lazy(CORE) { scope.findDependencyCrateRoot(CORE) }) return true

            RsFile.Attributes.NO_CORE -> Unit
        }
    }

    if (originalProcessor(ScopeEvent.STAR_IMPORTS)) {
        return false
    }

    if (ipm.withExternCrates && Namespace.Types in ns && scope is RsMod) {
        if (isEdition2018 && !scope.isCrateRoot) {
            val crateRoot = scope.crateRoot
            if (crateRoot != null) {
                val result = processWithShadowing(directlyDeclaredNames, processor) { shadowingProcessor ->
                    crateRoot.processExpandedItemsExceptImplsAndUses { item ->
                        if (item is RsExternCrateItem) {
                            processExternCrateItem(item, shadowingProcessor, true)
                        } else {
                            false
                        }
                    }
                }
                if (result) return true
            }
        }

        // "extern_prelude" feature. Extern crate names can be resolved as if they were in the prelude.
        // See https://blog.rust-lang.org/2018/10/25/Rust-1.30.0.html#module-system-improvements
        // See https://github.com/rust-lang/rust/pull/54404/
        val result = processWithShadowing(directlyDeclaredNames, processor) { shadowingProcessor ->
            val isCompletion = ipm == ItemProcessingMode.WITH_PRIVATE_IMPORTS_N_EXTERN_CRATES_COMPLETION
            processExternCrateResolveVariants(
                scope,
                isCompletion,
                // We don't want to process `self` as a crate root in this context b/c `self` should be
                // resolved to current module (processed on the upper level)
                withSelf = false,
                processor = shadowingProcessor
            )
        }
        if (result) return true
    }

    for ((isPublic, speck) in cachedItems.starImports) {
        if (!(isPublic || withPrivateImports)) continue

        val path = speck.path
        val basePath = if (path == null && speck.context is RsUseGroup) {
            // `use foo::bar::{self, *}`
            //           ~~~
            speck.qualifier
        } else {
            // `use foo::bar::*` or `use foo::{self, bar::*}`
            //           ~~~                         ~~~
            path
        }

        val mod = if (basePath != null) {
            // Issue https://github.com/intellij-rust/intellij-rust/issues/3989
            // BACKCOMPAT 2019.1
            val rootQualifier = generateSequence(basePath) { it.qualifier }.drop(1).lastOrNull()
            if (rootQualifier != null) {
                guard.doPreventingRecursion(rootQualifier, false) { basePath.reference?.resolve() }
            } else {
                basePath.reference?.resolve()
            }
        } else {
            speck.crateRoot
        } ?: continue

        val found = recursionGuard(mod, Computable {
            processItemOrEnumVariantDeclarations(mod, ns,
                { it.name !in directlyDeclaredNames && originalProcessor(it) },
                withPrivateImports = basePath != null && isSuperChain(basePath)
            )
        }, memoize = false)
        if (found == true) return true
    }

    return false
}

private val guard = RecursionManager.createGuard<PsiElement>("ItemResolution")

fun processExternCrateItem(item: RsExternCrateItem, processor: RsResolveProcessor, withPrivateImports: Boolean): Boolean {
    if (item.isPublic || withPrivateImports) {
        val mod = item.reference.resolve() ?: return false
        val nameWithAlias = item.nameWithAlias
        if (nameWithAlias != "self") {
            if (processor(nameWithAlias, mod)) return true
        } else {
            ItemResolutionTestmarks.externCrateSelfWithoutAlias.hit()
        }
    }
    return false
}

private fun processMultiResolveWithNs(name: String, ns: Set<Namespace>, ref: RsReference, processor: RsResolveProcessor): Boolean {
    // XXX: use items can legitimately resolve in both namespaces.
    // Because we must be lazy, we don't know up front how many times we
    // need to call the `processor`, so we need to calculate this lazily
    // if the processor scrutinizes at least the first element.

    // XXX: there are two `cfg`ed `boxed` modules in liballoc, so
    // we apply "first in the namespace wins" heuristic.
    // TODO fix after enabling #[cfg(...)] evaluation

    if (ns.size == 1) {
        // An optimized version for single namespace.
        return processor.lazy(name) {
            ref.multiResolve().find { it is RsNamedElement && ns.intersects(it.namespaces) }
        }
    }

    var variants: List<RsNamedElement> = emptyList()
    val visitedNamespaces = EnumSet.noneOf(Namespace::class.java)
    if (processor.lazy(name) {
        variants = ref.multiResolve()
            .filterIsInstance<RsNamedElement>()
            .filter { ns.intersects(it.namespaces) }
        val first = variants.firstOrNull()
        if (first != null) {
            visitedNamespaces.addAll(first.namespaces)
        }
        first
    }) {
        return true
    }
    // `variants` will be populated if processor looked at the corresponding element
    for (element in variants.drop(1)) {
        if (element.namespaces.all { it in visitedNamespaces }) continue
        visitedNamespaces.addAll(element.namespaces)
        if (processor(name, element)) return true
    }
    return false
}

enum class ItemProcessingMode(val withExternCrates: Boolean) {
    WITHOUT_PRIVATE_IMPORTS(false),
    WITH_PRIVATE_IMPORTS(false),
    WITH_PRIVATE_IMPORTS_N_EXTERN_CRATES(true),
    WITH_PRIVATE_IMPORTS_N_EXTERN_CRATES_COMPLETION(true);

    val cacheKey: Key<CachedValue<List<ScopeEntry>>> = Key.create("CACHED_ITEM_DECLS_$name")
}

object ItemResolutionTestmarks {
    val externCrateSelfWithoutAlias = Testmark("externCrateSelfWithoutAlias")
    val extraAtomUse = Testmark("extraAtomUse")
}
