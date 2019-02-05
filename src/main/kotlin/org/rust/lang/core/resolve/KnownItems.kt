/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.reference.SoftReference
import com.intellij.util.containers.ContainerUtil
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.util.AutoInjectedCrates.CORE
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.RsStructOrEnumItemElement
import org.rust.lang.core.psi.ext.cargoProject
import org.rust.lang.core.resolve.indexes.RsLangItemIndex
import org.rust.openapiext.getOrPutSoft
import java.util.*

private val KNOWN_ITEMS_KEY: Key<SoftReference<KnownItems>> = Key.create("KNOWN_ITEMS_KEY")
private val DUMMY_KNOWN_ITEMS = KnownItems(DummyKnownItemsLookup)

val CargoProject.knownItems: KnownItems
    get() = getOrPutSoft(KNOWN_ITEMS_KEY) {
        val workspace = workspace ?: return@getOrPutSoft DUMMY_KNOWN_ITEMS
        KnownItems(RealKnownItemsLookup(project, workspace))
    }

val RsElement.knownItems: KnownItems
    get() = cargoProject?.knownItems ?: DUMMY_KNOWN_ITEMS

/**
 * [CargoProject]-related object that allows to lookup rust items in project dependencies,
 * including the standard library.
 */
@Suppress("PropertyName")
class KnownItems(
    private val lookup: KnownItemsLookup
) {
    fun findLangItemRaw(langAttribute: String, crateName: String) =
        lookup.findLangItem(langAttribute, crateName)

    fun findItemRaw(path: String): RsNamedElement? =
        lookup.findItem(path)

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

    inline fun <reified T : RsNamedElement> findItem(path: String): T? =
        findItemRaw(path) as? T

    val Vec: RsStructOrEnumItemElement? get() = findItem("alloc::vec::Vec")
    val String: RsStructOrEnumItemElement? get() = findItem("alloc::string::String")
    val Arguments: RsStructOrEnumItemElement? get() = findItem("core::fmt::Arguments")
    val Option: RsStructOrEnumItemElement? get() = findItem("core::option::Option")
    val Result: RsStructOrEnumItemElement? get() = findItem("core::result::Result")

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
    val Try: RsTraitItem? get() = findItem("std::ops::Try")
    val Generator: RsTraitItem? get() = findItem("core::ops::Generator")

    // Lang items

    val Deref: RsTraitItem? get() = findLangItem("deref")
    val Sized: RsTraitItem? get() = findLangItem("sized")
    val Fn: RsTraitItem? get() = findLangItem("fn")
    val FnMut: RsTraitItem? get() = findLangItem("fn_mut")
    val FnOnce: RsTraitItem? get() = findLangItem("fn_once")
    val Index: RsTraitItem? get() = findLangItem("index")
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
    val Debug: RsTraitItem? get() = findLangItem("debug_trait")
    val Box: RsStructItem? get() = findLangItem("owned_box", "alloc")
}

interface KnownItemsLookup {
    fun findLangItem(langAttribute: String, crateName: String): RsNamedElement?
    fun findItem(path: String): RsNamedElement?
}

private object DummyKnownItemsLookup : KnownItemsLookup {
    override fun findLangItem(langAttribute: String, crateName: String): RsNamedElement? = null
    override fun findItem(path: String): RsNamedElement? = null
}

private class RealKnownItemsLookup(
    private val project: Project,
    private val workspace: CargoWorkspace
) : KnownItemsLookup {
    // WE use Optional because ConcurrentHashMap doesn't allow null values
    private val langItems: MutableMap<String, Optional<RsNamedElement>> = ContainerUtil.newConcurrentMap()
    private val resolvedItems: MutableMap<String, Optional<RsNamedElement>> = ContainerUtil.newConcurrentMap()

    override fun findLangItem(langAttribute: String, crateName: String): RsNamedElement? {
        return langItems.getOrPut(langAttribute) {
            Optional.ofNullable(RsLangItemIndex.findLangItem(project, langAttribute, crateName))
        }.orElse(null)
    }

    override fun findItem(path: String): RsNamedElement? {
        return resolvedItems.getOrPut(path) {
            Optional.ofNullable(resolveStringPath(path, workspace, project)?.first)
        }.orElse(null)
    }
}

enum class KnownDerivableTrait(
    private val resolver: (KnownItems) -> RsTraitItem?,
    val dependencies: Array<KnownDerivableTrait> = emptyArray()
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

    Serialize({ it.findItem("serde::Serialize") }),
    Deserialize({ it.findItem("serde::Deserialize") }),

    // TODO Fail also derives `Display`. Ignore it for now
    Fail({ it.findItem("failure::Fail") }, arrayOf(Debug)),
    ;

    fun findTrait(items: KnownItems): RsTraitItem? = resolver(items)
}

val KnownDerivableTrait.withDependencies: List<KnownDerivableTrait> get() = listOf(this, *dependencies)

val KNOWN_DERIVABLE_TRAITS: Map<String, KnownDerivableTrait> = KnownDerivableTrait.values().associate { it.name to it }
