/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.openapiext.toPsiFile
import java.util.*

fun processItemOrEnumVariantDeclarations(
    scope: RsElement,
    ns: Set<Namespace>,
    processor: RsResolveProcessor,
    withPrivateImports: Boolean = false
): Boolean {
    when (scope) {
        is RsEnumItem -> {
            if (processAll(scope.enumBody?.enumVariantList.orEmpty(), processor)) return true
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

    fun processItem(item: RsItemElement): Boolean {
        when (item) {
            is RsUseItem ->
                if (item.isPublic || withPrivateImports) {
                    val rootSpeck = item.useSpeck ?: return false
                    forEachLeafSpeck(rootSpeck) { speck ->
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
                val name = item.alias?.name ?: item.name ?: return false
                val mod = item.reference.resolve() ?: return false
                if (processor(name, mod)) return true
            }
        }
        return false
    }

    if (scope.processExpandedItems(::processItem)) return true


    if (Namespace.Types in ns) {
        if (scope is RsFile && scope.isCrateRoot) {
            val pkg = scope.containingCargoPackage

            if (pkg != null) {
                val findStdMod = { name: String ->
                    val crate = pkg.findDependency(name)?.crateRoot
                    crate?.toPsiFile(scope.project)?.rustMod
                }

                // Rust injects implicit `extern crate std` in every crate root module unless it is
                // a `#![no_std]` crate, in which case `extern crate core` is injected. However, if
                // there is a (unstable?) `#![no_core]` attribute, nothing is injected.
                //
                // https://doc.rust-lang.org/book/using-rust-without-the-standard-library.html
                // The stdlib lib itself is `#![no_std]`, and the core is `#![no_core]`
                when (scope.attributes) {
                    RsFile.Attributes.NONE ->
                        if (processor.lazy("std") { findStdMod("std") }) return true

                    RsFile.Attributes.NO_STD ->
                        if (processor.lazy("core") { findStdMod("core") }) return true

                    RsFile.Attributes.NO_CORE -> Unit
                }
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
        val basePath = speck.path
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

private val RsUseSpeck.nameInScope: String? get() {
    alias?.name?.let { return it }
    val baseName = path?.referenceName ?: return null
    if (baseName == "self") {
        NameResolutionTestmarks.selfInGroupName.hit()
        return qualifier?.referenceName
    }
    return baseName
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

fun forEachLeafSpeck(root: RsUseSpeck, consumer: (RsUseSpeck) -> Unit) {
    val group = root.useGroup
    if (group == null) consumer(root) else group.useSpeckList.forEach { forEachLeafSpeck(it, consumer) }
}
