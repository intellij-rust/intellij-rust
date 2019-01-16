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
    private fun findLangItem(langAttribute: String, crateName: String = CORE): RsTraitItem? =
        lookup.findLangItem(langAttribute, crateName)

    fun findItem(path: String): RsNamedElement? =
        lookup.findItem(path)

    fun findStructOrEnum(path: String): RsStructOrEnumItemElement? =
        findItem(path) as? RsStructOrEnumItemElement

    fun findTrait(path: String): RsTraitItem? =
        findItem(path) as? RsTraitItem

    val Vec: RsStructOrEnumItemElement? get() = findStructOrEnum("alloc::vec::Vec")
    val String: RsStructOrEnumItemElement? get() = findStructOrEnum("alloc::string::String")
    val Arguments: RsStructOrEnumItemElement? get() = findStructOrEnum("core::fmt::Arguments")
    val Option: RsStructOrEnumItemElement? get() = findStructOrEnum("core::option::Option")
    val Result: RsStructOrEnumItemElement? get() = findStructOrEnum("core::result::Result")

    val Iterator: RsTraitItem? get() = findTrait("core::iter::Iterator")
    val IntoIterator: RsTraitItem? get() = findTrait("core::iter::IntoIterator")
    val AsRef: RsTraitItem? get() = findTrait("core::convert::AsRef")
    val AsMut: RsTraitItem? get() = findTrait("core::convert::AsMut")
    val From: RsTraitItem? get() = findTrait("core::convert::From")
    val TryFrom: RsTraitItem? get() = findTrait("core::convert::TryFrom")
    val FromStr: RsTraitItem? get() = findTrait("core::str::FromStr")
    val Borrow: RsTraitItem? get() = findTrait("core::borrow::Borrow")
    val BorrowMut: RsTraitItem? get() = findTrait("core::borrow::BorrowMut")
    val Hash: RsTraitItem? get() = findTrait("core::hash::Hash")
    val Default: RsTraitItem? get() = findTrait("core::default::Default")
    val Display: RsTraitItem? get() = findTrait("core::fmt::Display")
    val ToOwned: RsTraitItem? get() = findTrait("alloc::borrow::ToOwned")
    val ToString: RsTraitItem? get() = findTrait("alloc::string::ToString")

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
    // `Eq` trait doesn't have its own lang attribute, so use `findCoreItem` to find it
    val Eq: RsTraitItem? get() = findTrait("core::cmp::Eq")
    // In some old versions of stdlib `PartialOrd` trait has a lang attribute with value "ord",
    // but in the new stdlib it is "partial_ord" ("ord" is used for "Ord" trait). So we try
    // "partial_ord", and on failure we resolve it by path
    val PartialOrd: RsTraitItem? get() = findLangItem("partial_ord") ?: findTrait("core::cmp::PartialOrd")
    // Some old versions of stdlib contain `Ord` trait without lang attribute
    val Ord: RsTraitItem? get() = findTrait("core::cmp::Ord")
    val Debug: RsTraitItem? get() = findLangItem("debug_trait")
}

interface KnownItemsLookup {
    fun findLangItem(langAttribute: String, crateName: String): RsTraitItem?
    fun findItem(path: String): RsNamedElement?
}

private object DummyKnownItemsLookup : KnownItemsLookup {
    override fun findLangItem(langAttribute: String, crateName: String): RsTraitItem? = null
    override fun findItem(path: String): RsNamedElement? = null
}

private class RealKnownItemsLookup(
    private val project: Project,
    private val workspace: CargoWorkspace
) : KnownItemsLookup {
    // WE use Optional because ConcurrentHashMap doesn't allow null values
    private val langItems: MutableMap<String, Optional<RsTraitItem>> = ContainerUtil.newConcurrentMap()
    private val resolvedItems: MutableMap<String, Optional<RsNamedElement>> = ContainerUtil.newConcurrentMap()

    override fun findLangItem(langAttribute: String, crateName: String): RsTraitItem? {
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
