/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.cargo.util.AutoInjectedCrates.CORE
import org.rust.cargo.util.AutoInjectedCrates.STD
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.stdext.intersects
import java.util.*

fun processItemOrEnumVariantDeclarations(
    scope: RsElement,
    ns: Set<Namespace>,
    processor: RsResolveProcessor,
    withPrivateImports: Boolean = false
): Boolean {
    when (scope) {
        is RsEnumItem -> {
            if (processAll(scope.variants, processor)) return true
        }
        is RsMod -> {
            if (processItemDeclarations(scope, ns, processor, withPrivateImports)) return true
        }
    }

    return false
}

fun processItemDeclarations(
    scope: RsItemsOwner,
    ns: Set<Namespace>,
    originalProcessor: RsResolveProcessor,
    withPrivateImports: Boolean
): Boolean {
    val starImports = mutableListOf<RsUseSpeck>()
    val itemImports = mutableListOf<RsUseSpeck>()

    val directlyDeclaredNames = HashSet<String>()
    val processor = { e: ScopeEntry ->
        directlyDeclaredNames += e.name
        originalProcessor(e)
    }

    loop@ for (item in scope.expandedItemsExceptImpls) {
        when (item) {
            is RsUseItem ->
                if (item.isPublic || withPrivateImports) {
                    val rootSpeck = item.useSpeck ?: continue@loop
                    rootSpeck.forEachLeafSpeck { speck ->
                        (if (speck.isStarImport) starImports else itemImports) += speck
                    }
                }

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
                if (processAll(item.functionList, processor) || processAll(item.constantList, processor)) return true
            }

            is RsExternCrateItem -> {
                if (item.isPublic || withPrivateImports) {
                    val mod = item.reference.resolve() ?: continue@loop
                    if (processor(item.nameWithAlias, mod)) return true
                }
            }
        }
    }

    if (Namespace.Types in ns) {
        if (scope is RsFile && scope.isCrateRoot && withPrivateImports) {
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
    }

    for (speck in itemImports) {
        check(speck.useGroup == null)
        val path = speck.path ?: continue
        val name = speck.nameInScope ?: continue
        if (processMultiResolveWithNs(name, ns, path.reference, processor)) return true
    }

    if (originalProcessor(ScopeEvent.STAR_IMPORTS)) {
        return false
    }
    for (speck in starImports) {
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
        val mod = (if (basePath != null) basePath.reference.resolve() else speck.crateRoot)
            ?: continue

        val found = processItemOrEnumVariantDeclarations(mod, ns,
            { it.name !in directlyDeclaredNames && originalProcessor(it) },
            withPrivateImports = basePath != null && isSuperChain(basePath)
        )
        if (found) return true
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
