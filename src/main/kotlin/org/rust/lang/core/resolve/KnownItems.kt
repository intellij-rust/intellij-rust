/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.ThreeState
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.util.AutoInjectedCrates.CORE
import org.rust.lang.core.macros.proc.ProcMacroApplicationService
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psi.rustStructureModificationTracker
import org.rust.lang.core.resolve.indexes.RsLangItemIndex
import org.rust.lang.core.types.asTy
import org.rust.lang.core.types.ty.*
import org.rust.openapiext.isUnitTestMode
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private val KNOWN_ITEMS_KEY: Key<CachedValue<KnownItems>> = Key.create("KNOWN_ITEMS_KEY")
private val DUMMY_KNOWN_ITEMS = KnownItems(DummyKnownItemsLookup)

val CargoProject.knownItems: KnownItems
    get() = CachedValuesManager.getManager(project).getCachedValue(this, KNOWN_ITEMS_KEY, {
        val items = run {
            val workspace = workspace ?: return@run DUMMY_KNOWN_ITEMS
            KnownItems(RealKnownItemsLookup(project, workspace))
        }
        CachedValueProvider.Result(items, project.rustStructureModificationTracker)
    }, false)

val RsElement.knownItems: KnownItems
    get() = cargoProject?.knownItems ?: DUMMY_KNOWN_ITEMS

/**
 * [CargoProject]-related object that allows to lookup rust items in project dependencies,
 * including the standard library.
 */
class KnownItems(
    private val lookup: KnownItemsLookup
) {
    fun findLangItemRaw(langAttribute: String, crateName: String) =
        lookup.findLangItem(langAttribute, crateName)

    fun findItemRaw(path: String, isStd: Boolean): RsNamedElement? =
        lookup.findItem(path, isStd)

    /**
     * Find some known item by its "lang" attribute
     * ```rust
     * #[lang = "deref"]
     * trait Deref { ... }
     * ```
     */
    inline fun <reified T : RsNamedElement> findLangItem(
        langAttribute: String,
        crateName: String = CORE
    ): T? = findLangItemRaw(langAttribute, crateName) as? T

    inline fun <reified T : RsNamedElement> findItem(path: String, isStd: Boolean = true): T? =
        findItemRaw(path, isStd) as? T

    val Vec: RsStructOrEnumItemElement? get() = findItem("alloc::vec::Vec")
    val String: RsStructOrEnumItemElement? get() = findItem("alloc::string::String")
    val Arguments: RsStructOrEnumItemElement? get() = findItem("core::fmt::Arguments")
    val Option: RsStructOrEnumItemElement? get() = findItem("core::option::Option")
    val Result: RsStructOrEnumItemElement? get() = findItem("core::result::Result")
    val Rc: RsStructOrEnumItemElement? get() = findItem("alloc::rc::Rc")
    val Arc: RsStructOrEnumItemElement? get() = findItem("alloc::sync::Arc") ?: findItem("alloc::arc::Arc")
    val Cell: RsStructOrEnumItemElement? get() = findItem("core::cell::Cell")
    val RefCell: RsStructOrEnumItemElement? get() = findItem("core::cell::RefCell")
    val UnsafeCell: RsStructOrEnumItemElement? get() = findItem("core::cell::UnsafeCell")
    val Mutex: RsStructOrEnumItemElement? get() = findItem("std::sync::mutex::Mutex")
    val Path: RsStructOrEnumItemElement? get() = findItem("std::path::Path")
    val PathBuf: RsStructOrEnumItemElement? get() = findItem("std::path::PathBuf")

    val Iterator: RsTraitItem? get() = findItem("core::iter::Iterator")
    val IntoIterator: RsTraitItem? get() = findItem("core::iter::IntoIterator")
    val AsRef: RsTraitItem? get() = findItem("core::convert::AsRef")
    val AsMut: RsTraitItem? get() = findItem("core::convert::AsMut")
    val From: RsTraitItem? get() = findItem("core::convert::From")
    val TryFrom: RsTraitItem? get() = findItem("core::convert::TryFrom")
    val FromStr: RsTraitItem? get() = findItem("core::str::FromStr")
    val Borrow: RsTraitItem? get() = findItem("core::borrow::Borrow")
    val BorrowMut: RsTraitItem? get() = findItem("core::borrow::BorrowMut")
    val Hash: RsTraitItem? get() = findItem("core::hash::Hash")
    val Default: RsTraitItem? get() = findItem("core::default::Default")
    val Display: RsTraitItem? get() = findItem("core::fmt::Display")
    val ToOwned: RsTraitItem? get() = findItem("alloc::borrow::ToOwned")
    val ToString: RsTraitItem? get() = findItem("alloc::string::ToString")
    val Try: RsTraitItem? get() = findItem("core::ops::try_trait::Try") ?: findItem("core::ops::try::Try")
    val Generator: RsTraitItem? get() = findItem("core::ops::generator::Generator")
    val Future: RsTraitItem? get() = findItem("core::future::future::Future")
    val IntoFuture: RsTraitItem? get() = findItem("core::future::into_future::IntoFuture")
    val Octal: RsTraitItem? get() = findItem("core::fmt::Octal")
    val LowerHex: RsTraitItem? get() = findItem("core::fmt::LowerHex")
    val UpperHex: RsTraitItem? get() = findItem("core::fmt::UpperHex")
    val Pointer: RsTraitItem? get() = findItem("core::fmt::Pointer")
    val Binary: RsTraitItem? get() = findItem("core::fmt::Binary")
    val LowerExp: RsTraitItem? get() = findItem("core::fmt::LowerExp")
    val UpperExp: RsTraitItem? get() = findItem("core::fmt::UpperExp")

    // Lang items

    val Deref: RsTraitItem? get() = findLangItem("deref")
    val Drop: RsTraitItem? get() = findLangItem("drop")
    val Sized: RsTraitItem? get() = findLangItem("sized")
    val Unsize: RsTraitItem? get() = findLangItem("unsize")
    val CoerceUnsized: RsTraitItem? get() = findLangItem("coerce_unsized")
    val Destruct: RsTraitItem? get() = findLangItem("destruct")
    val Fn: RsTraitItem? get() = findLangItem("fn")
    val FnMut: RsTraitItem? get() = findLangItem("fn_mut")
    val FnOnce: RsTraitItem? get() = findLangItem("fn_once")
    val Index: RsTraitItem? get() = findLangItem("index")
    val IndexMut: RsTraitItem? get() = findLangItem("index_mut")
    val Clone: RsTraitItem? get() = findLangItem("clone")
    val Copy: RsTraitItem? get() = findLangItem("copy")
    val PartialEq: RsTraitItem? get() = findLangItem("eq")

    // `Eq` trait doesn't have its own lang attribute, so use `findItem` to find it
    val Eq: RsTraitItem? get() = findItem("core::cmp::Eq")

    // In some old versions of stdlib `PartialOrd` trait has a lang attribute with value "ord",
    // but in the new stdlib it is "partial_ord" ("ord" is used for "Ord" trait). So we try
    // "partial_ord", and on failure we resolve it by path
    val PartialOrd: RsTraitItem? get() = findLangItem("partial_ord") ?: findItem("core::cmp::PartialOrd")

    // Some old versions of stdlib contain `Ord` trait without lang attribute
    val Ord: RsTraitItem? get() = findItem("core::cmp::Ord")

    // Some stdlib versions don't have direct `#[lang="debug_trait"]` attribute on `Debug` trait.
    // In this case, fully qualified name search is used
    val Debug: RsTraitItem? get() = findLangItem("debug_trait") ?: findItem("core::fmt::Debug")
    val Box: RsStructOrEnumItemElement? get() = findLangItem("owned_box", "alloc")
    val Pin: RsStructOrEnumItemElement? get() = findLangItem("pin", "core")

    val drop: RsFunction? get() = findItem("core::mem::drop")
}

interface KnownItemsLookup {
    fun findLangItem(langAttribute: String, crateName: String): RsNamedElement?
    fun findItem(path: String, isStd: Boolean): RsNamedElement?
}

private object DummyKnownItemsLookup : KnownItemsLookup {
    override fun findLangItem(langAttribute: String, crateName: String): RsNamedElement? = null
    override fun findItem(path: String, isStd: Boolean): RsNamedElement? = null
}

private class RealKnownItemsLookup(
    private val project: Project,
    private val workspace: CargoWorkspace
) : KnownItemsLookup {
    // WE use Optional because ConcurrentHashMap doesn't allow null values
    private val langItems: MutableMap<String, Optional<RsNamedElement>> = ConcurrentHashMap()
    private val resolvedItems: MutableMap<String, Optional<RsNamedElement>> = ConcurrentHashMap()

    override fun findLangItem(langAttribute: String, crateName: String): RsNamedElement? {
        return langItems.getOrPut(langAttribute) {
            Optional.ofNullable(RsLangItemIndex.findLangItem(project, langAttribute, crateName))
        }.orElse(null)
    }

    override fun findItem(path: String, isStd: Boolean): RsNamedElement? {
        return resolvedItems.getOrPut(path) {
            Optional.ofNullable(resolveStringPath(path, workspace, project, ThreeState.fromBoolean(isStd))?.first)
        }.orElse(null)
    }
}

enum class KnownDerivableTrait(
    private val resolver: (KnownItems) -> RsTraitItem?,
    val dependencies: Array<KnownDerivableTrait> = emptyArray(),
    val isStd: Boolean = true
) {
    Clone(KnownItems::Clone),
    Copy(KnownItems::Copy, arrayOf(Clone)),
    Debug(KnownItems::Debug),
    Default(KnownItems::Default),
    Hash(KnownItems::Hash),
    PartialEq(KnownItems::PartialEq),
    Eq(KnownItems::Eq, arrayOf(PartialEq)),
    PartialOrd(KnownItems::PartialOrd, arrayOf(PartialEq)),
    Ord(KnownItems::Ord, arrayOf(PartialOrd, Eq, PartialEq)),

    Serialize({ it.findItem("serde::Serialize", isStd = false) }, isStd = false),
    Deserialize({ it.findItem("serde::Deserialize", isStd = false) }, isStd = false),

    // TODO Fail also derives `Display`. Ignore it for now
    Fail({ it.findItem("failure::Fail", isStd = false) }, arrayOf(Debug), isStd = false),
    ;

    fun findTrait(items: KnownItems): RsTraitItem? = resolver(items)

    /** Hardcoded trait impl vs proc macro expansion usage */
    fun shouldUseHardcodedTraitDerive(): Boolean {
        // We don't use hardcoded impls for non-std derives if proc macro expansion is enabled
        return isStd || !ProcMacroApplicationService.isDeriveEnabled()
    }
}

val KnownDerivableTrait.withDependencies: List<KnownDerivableTrait> get() = listOf(this, *dependencies)

val KNOWN_DERIVABLE_TRAITS: Map<String, KnownDerivableTrait> = KnownDerivableTrait.values().associateBy { it.name }

enum class KnownMacro(val macroName: String) {
    Format("format"),
    Vec("vec"),
    AddrOf("addr_of"),
    AddrOfMut("addr_of_mut"),
    Env("env"),
    ;

    fun retTy(items: KnownItems): Ty {
        return when (this) {
            Format -> items.String.asTy()
            Vec -> items.Vec.asTy(TyUnknown)
            AddrOf -> TyPointer(TyUnknown, Mutability.IMMUTABLE)
            AddrOfMut -> TyPointer(TyUnknown, Mutability.MUTABLE)
            Env -> TyReference(TyStr.INSTANCE, Mutability.IMMUTABLE)
        }
    }

    companion object {
        private val NAME_TO_VALUE: Map<String, KnownMacro> = values().associateBy { it.macroName }

        fun of(macro: RsMacroDefinitionBase): KnownMacro? {
            val isStdMacro = macro.containingCrate.origin == PackageOrigin.STDLIB
                || isUnitTestMode && macro.queryAttributes.hasAttribute("intellij_rust_std_macro")
            if (!isStdMacro) return null
            val name = macro.name ?: return null
            return NAME_TO_VALUE[name]
        }
    }
}
