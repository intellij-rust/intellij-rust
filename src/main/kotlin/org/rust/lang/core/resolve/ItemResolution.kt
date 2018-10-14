/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.cargo.util.AutoInjectedCrates.CORE
import org.rust.cargo.util.AutoInjectedCrates.STD
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ItemResolutionTestmarks.externCrateItemAliasWithSameName
import org.rust.lang.core.resolve.ItemResolutionTestmarks.externCrateItemWithoutAlias
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.openapiext.Testmark
import java.util.*

fun processItemOrEnumVariantDeclarations(
    scope: RsElement,
    ns: Set<Namespace>,
    processor: RsResolveProcessor,
    withPrivateImports: Boolean = false,
    withPlainExternCrateItems: Boolean = true
): Boolean {
    when (scope) {
        is RsEnumItem -> {
            if (processAll(scope.enumBody?.enumVariantList.orEmpty(), processor)) return true
        }
        is RsMod -> {
            if (processItemDeclarations(scope, ns, processor, withPrivateImports, withPlainExternCrateItems)) return true
        }
    }

    return false
}


fun processItemDeclarations(
    scope: RsItemsOwner,
    ns: Set<Namespace>,
    originalProcessor: RsResolveProcessor,
    withPrivateImports: Boolean,
    withPlainExternCrateItems: Boolean = true
): Boolean {
    val starImports = mutableListOf<RsUseSpeck>()
    val itemImports = mutableListOf<RsUseSpeck>()

    val directlyDeclaredNames = HashSet<String>()
    val processor = { e: ScopeEntry ->
        directlyDeclaredNames += e.name
        originalProcessor(e)
    }

    fun processItem(item: RsItemElement): Boolean {
        when (item) {
            is RsUseItem ->
                if (item.isPublic || withPrivateImports) {
                    val rootSpeck = item.useSpeck ?: return false
                    rootSpeck.forEachLeafSpeck { speck ->
                        (if (speck.isStarImport) starImports else itemImports) += speck
                    }
                }

            // Unit like structs are both types and values
            is RsStructItem ->
                if (item.namespaces.intersect(ns).isNotEmpty() && processor(item)) return true

            is RsModDeclItem -> if (Namespace.Types in ns) {
                val name = item.name ?: return false
                val mod = item.reference.resolve() ?: return false
                if (processor(name, mod)) return true
            }

            is RsEnumItem, is RsModItem, is RsTraitItem, is RsTypeAlias ->
                if (Namespace.Types in ns && processor(item as RsNamedElement)) return true

            is RsFunction, is RsConstant ->
                if (Namespace.Values in ns && processor(item as RsNamedElement)) return true

            is RsForeignModItem ->
                if (processAll(item.functionList, processor) || processAll(item.constantList, processor)) return true

            is RsExternCrateItem -> {
                if (item.isPublic || withPrivateImports) {
                    val itemName = item.name
                    val aliasName = item.alias?.name
                    val name = aliasName ?: itemName ?: return false

                    if (!withPlainExternCrateItems) {
                        // In some situations (for example, absolute paths in edition 2018)
                        // we should process only extern crate item
                        // which brings new name into the scope, i.e. with alias,
                        // because otherwise this item is already processed in other place
                        if (aliasName == null) {
                            externCrateItemWithoutAlias.hit()
                            return false
                        }
                        if (aliasName == itemName) {
                            externCrateItemAliasWithSameName.hit()
                            return false
                        }
                    }
                    val mod = item.reference.resolve() ?: return false
                    if (processor(name, mod)) return true
                }
            }
        }
        return false
    }

    if (scope.processExpandedItems(::processItem)) return true


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
            withPrivateImports = basePath != null && isSuperChain(basePath),
            withPlainExternCrateItems = withPlainExternCrateItems
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
            .filter { ns.intersect(it.namespaces).isNotEmpty() }
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

object ItemResolutionTestmarks {
    val externCrateItemWithoutAlias = Testmark("externCrateItemWithoutAlias")
    val externCrateItemAliasWithSameName = Testmark("externCrateItemAliasWithSameName")
}
