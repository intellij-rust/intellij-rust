/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.psi.PsiElement
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.lang.core.completion.getOriginalOrSelf
import org.rust.lang.core.macros.decl.MACRO_DOLLAR_CRATE_IDENTIFIER
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.RsPathResolveKind.*
import org.rust.lang.core.resolve2.RsModInfo
import org.rust.lang.core.resolve2.getModInfo
import kotlin.LazyThreadSafetyMode.NONE

class PathResolutionContext(
    val context: RsElement,
    val isCompletion: Boolean,
    val processAssocItems: Boolean,
    private val givenImplLookup: ImplLookup?,
) {
    val crateRoot: RsMod? = context.crateRoot
    private var lazyContainingMod: Lazy<RsMod> = lazy(NONE) {
        context.containingMod
    }
    val containingMod: RsMod get() = lazyContainingMod.value
    var lazyContainingModInfo: Lazy<RsModInfo?> = lazy(NONE) {
        getModInfo(containingMod)
    }
    val containingModInfo: RsModInfo? get() = lazyContainingModInfo.value
    val implLookup: ImplLookup by lazy(NONE) {
        givenImplLookup ?: ImplLookup.relativeTo(context)
    }
    val isAtLeastEdition2018: Boolean
        get() {
            val edition = crateRoot?.edition ?: CargoWorkspace.Edition.DEFAULT
            return edition >= CargoWorkspace.Edition.EDITION_2018
        }

    fun getContainingModInfo(knownContainingMod: RsMod): RsModInfo? {
        if (lazyContainingModInfo.isInitialized()) {
            return lazyContainingModInfo.value
        }
        @Suppress("NAME_SHADOWING")
        val knownContainingMod = knownContainingMod.getOriginalOrSelf()
        if (lazyContainingMod.isInitialized()) {
            check(lazyContainingMod.value == knownContainingMod)
        }
        val modInfo = getModInfo(knownContainingMod)
        lazyContainingMod = lazyOf(knownContainingMod)
        lazyContainingModInfo = lazyOf(modInfo)
        return modInfo
    }

    fun classifyPath(path: RsPath): RsPathResolveKind {
        val parent = path.stubParent
        if (parent is RsMacroCall) {
            error("Tried to use `processPathResolveVariants` for macro path. See `RsMacroPathReferenceImpl`")
        }
        if (parent is RsAssocTypeBinding) {
            return AssocTypeBindingPath(parent)
        }

        val qualifier = path.qualifier
        val typeQual = path.typeQual
        val ns = path.allowedNamespaces(isCompletion, parent)

        return when {
            // foo::bar
            qualifier != null ->
                QualifiedPath(path, ns, qualifier, parent)

            // `<T as Trait>::Item` or `<T>::Item`
            typeQual != null ->
                ExplicitTypeQualifiedPath(ns, typeQual)

            else -> classifyUnqualifiedPath(path, ns)
        }
    }

    private fun classifyUnqualifiedPath(
        path: RsPath,
        ns: Set<Namespace>,
    ): RsPathResolveKind {
        /** Path starts with `::` */
        val hasColonColon = path.hasColonColon
        if (!hasColonColon) {
            // hacks around $crate macro metavar. See `expandDollarCrateVar` function docs
            val referenceName = path.referenceName
            if (referenceName == MACRO_DOLLAR_CRATE_IDENTIFIER) {
                return MacroDollarCrateIdentifier(path)
            }
        }

        val isAtLeastEdition2018 = isAtLeastEdition2018

        // In 2015 edition a path is crate-relative (global) if it's inside use item,
        // inside "visibility restriction" or if it starts with `::`
        // ```rust, edition2015
        // use foo::bar; // `foo` is crate-relative
        // let a = ::foo::bar; // `foo` is also crate-relative
        // pub(in foo::bar) fn baz() {}
        //       //^ crate-relative path too
        // ```
        // Starting 2018 edition a path is crate-relative if it starts with `crate::` (handled above)
        // or if it's inside "visibility restriction". `::`-qualified path since 2018 edition means that
        // such path is a name of some dependency crate (that should be resolved without `extern crate`)
        val isCrateRelative = !isAtLeastEdition2018 && (hasColonColon || path.rootPath().parent is RsUseSpeck)
            || path.rootPath().parent is RsVisRestriction
        // see https://doc.rust-lang.org/edition-guide/rust-2018/module-system/path-clarity.html#the-crate-keyword-refers-to-the-current-crate
        val isExternCrate = isAtLeastEdition2018 && hasColonColon
        return when {
            isCrateRelative -> CrateRelativePath(path, ns, hasColonColon)

            isExternCrate -> ExternCratePath

            else -> UnqualifiedPath(ns)
        }
    }
}

sealed class RsPathResolveKind {
    /** A path consist of a single identifier, e.g. `foo` */
    data class UnqualifiedPath(val ns: Set<Namespace>) : RsPathResolveKind()

    /** `bar` in `foo::bar` or `use foo::{bar}` */
    class QualifiedPath(
        val path: RsPath,
        val ns: Set<Namespace>,
        val qualifier: RsPath,
        val parent: PsiElement?
    ) : RsPathResolveKind()

    /** `<Foo>::bar` or `<Foo as Bar>::baz` */
    class ExplicitTypeQualifiedPath(
        val ns: Set<Namespace>,
        val typeQual: RsTypeQual
    ) : RsPathResolveKind()

    /** @see MACRO_DOLLAR_CRATE_IDENTIFIER */
    class MacroDollarCrateIdentifier(val path: RsPath) : RsPathResolveKind()

    /**
     * ```
     * pub (in foo) struct Bar;
     *       //^ this path is crate-relative
     * ```
     *
     * Also, in 2015 edition a path is crate-relative if it starts with `::` or it is a first path
     * segment in a `use` item.
     */
    class CrateRelativePath(
        val path: RsPath,
        val ns: Set<Namespace>,
        val hasColonColon: Boolean,
    ) : RsPathResolveKind()

    /** A path starting with `::` since 2018 edition */
    object ExternCratePath : RsPathResolveKind()

    /** `Item` path in `dyn Iterator<Item = u8>` */
    class AssocTypeBindingPath(val parent: RsAssocTypeBinding) : RsPathResolveKind()
}
