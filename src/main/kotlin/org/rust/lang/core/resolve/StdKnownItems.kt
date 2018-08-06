/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.util.AutoInjectedCrates
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.RsTypeDeclarationElement
import org.rust.lang.core.psi.ext.cargoWorkspace
import org.rust.lang.core.resolve.indexes.RsLangItemIndex
import org.rust.lang.core.types.infer.substitute
import org.rust.lang.core.types.toTypeSubst
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.ty.getTypeParameter
import org.rust.openapiext.ProjectCache
import java.util.*

class StdKnownItems private constructor(
    private val absolutePathResolver: (String, String) -> RsNamedElement?,
    private val langItemsResolver: (String, String) -> RsTraitItem?
) {

    fun findStdItem(prefixNoStd: String, name: String): RsNamedElement? =
        absolutePathResolver(prefixNoStd, name)

    private fun findStdTy(prefixNoStd: String, name: String): Ty {
        val element = findStdItem(prefixNoStd, name) ?: return TyUnknown
        return (element as? RsTypeDeclarationElement)?.declaredType ?: TyUnknown
    }

    fun findCoreItem(name: String): RsNamedElement? =
        findStdItem("core", name)

    fun findCoreTy(name: String): Ty =
        findStdTy("core", name)

    fun findIteratorTrait(): RsTraitItem? =
        findCoreItem("iter::Iterator") as? RsTraitItem

    fun findVecForElementTy(elementTy: Ty): Ty {
        val ty = findStdTy("alloc", "vec::Vec")

        val typeParameter = ty.getTypeParameter("T") ?: return ty
        return ty.substitute(mapOf(typeParameter to elementTy).toTypeSubst())
    }

    fun findRangeTy(rangeName: String, indexType: Ty?): Ty {
        val ty = findStdTy("core", "ops::" + rangeName)

        if (indexType == null) return ty

        val typeParameter = ty.getTypeParameter("Idx") ?: return ty
        return ty.substitute(mapOf(typeParameter to indexType).toTypeSubst())
    }

    fun findStringTy(): Ty =
        findStdTy("alloc", "string::String")

    fun findArgumentsTy(): Ty =
        findCoreTy("fmt::Arguments")

    fun findOptionItem(): RsNamedElement? =
        findCoreItem("option::Option")

    fun findResultItem(): RsNamedElement? =
        findCoreItem("result::Result")

    fun findAsRefTrait(): RsTraitItem? =
        findCoreItem("convert::AsRef") as? RsTraitItem

    fun findAsMutTrait(): RsTraitItem? =
        findCoreItem("convert::AsMut") as? RsTraitItem

    fun findFromTrait(): RsTraitItem? =
        findCoreItem("convert::From") as? RsTraitItem

    fun findTryFromTrait(): RsTraitItem? =
        findCoreItem("convert::TryFrom") as? RsTraitItem

    fun findFromStrTrait(): RsTraitItem? =
        findCoreItem("str::FromStr") as? RsTraitItem

    fun findBorrowTrait(): RsTraitItem? =
        findCoreItem("borrow::Borrow") as? RsTraitItem

    fun findBorrowMutTrait(): RsTraitItem? =
        findCoreItem("borrow::BorrowMut") as? RsTraitItem

    fun findToOwnedTrait(): RsTraitItem? =
        findCoreItem("borrow::ToOwned") as? RsTraitItem

    fun findToStringTrait(): RsTraitItem? =
        findCoreItem("string::ToString") as? RsTraitItem

    private fun findLangItem(langAttribute: String, crateName: String = AutoInjectedCrates.CORE): RsTraitItem? =
        langItemsResolver(langAttribute, crateName)

    fun findCloneTrait(): RsTraitItem? = findLangItem("clone")
    fun findCopyTrait(): RsTraitItem? = findLangItem("copy")

    fun findPartialEqTrait(): RsTraitItem? = findLangItem("eq")

    // `Eq` trait doesn't have own lang attribute, so use `findCoreItem` to find it
    fun findEqTrait(): RsTraitItem? = findCoreItem("cmp::Eq") as? RsTraitItem

    // In some old versions of stdlib `PartialOrd` trait has lang attribute with value "ord",
    // but in the new stdlib it is "partial_ord" ("ord" used for "Ord" trait). So we try
    // "partial_ord", and on failure we resolve it by path
    fun findPartialOrdTrait(): RsTraitItem? =
        findLangItem("partial_ord") ?: findCoreItem("cmp::PartialOrd") as? RsTraitItem

    // Some old versions of stdlib contain `Ord` trait without lang attribute
    fun findOrdTrait(): RsTraitItem? =
        findCoreItem("cmp::Ord") as? RsTraitItem

    fun findHashTrait(): RsTraitItem? = findCoreItem("hash::Hash") as? RsTraitItem

    fun findDebugTrait(): RsTraitItem? = findLangItem("debug_trait")

    fun findDefaultTrait(): RsTraitItem? = findCoreItem("default::Default") as? RsTraitItem

    fun findSizedTrait(): RsTraitItem? = findLangItem("sized")
    fun findSyncTrait(): RsTraitItem? = findLangItem("sync")
    fun findSendTrait(): RsTraitItem? = findLangItem("send")

    companion object {
        private val stdKnownItemsCache =
            ProjectCache<Pair<CargoWorkspace, String>, Optional<RsNamedElement>>("stdKnownItemsCache")

        private val langItemsCache =
            ProjectCache<String, Optional<RsTraitItem>>("langItemsCache")

        private val defaultStdKnownItems = StdKnownItems({ _, _ -> null }, { _, _ -> null })

        fun relativeTo(psi: RsElement): StdKnownItems {
            val project = psi.project
            val workspace = psi.cargoWorkspace ?: return defaultStdKnownItems
            val crateRoot = psi.crateRoot as? RsFile ?: return defaultStdKnownItems
            val useStdPrefix = crateRoot.attributes == RsFile.Attributes.NONE

            val absolutePathResolver: (String, String) -> RsNamedElement? = { prefixNoStd, name ->
                val prefix = if (useStdPrefix) "std" else prefixNoStd
                val path = "$prefix::$name"
                val key = workspace to path
                stdKnownItemsCache.getOrPut(project, key) {
                    Optional.ofNullable(resolveStringPath(path, workspace, project)?.first)
                }.orElse(null)
            }

            val langItemsResolver: (String, String) -> RsTraitItem? = { langAttribute, crateName ->
                langItemsCache.getOrPut(project, langAttribute) {
                    Optional.ofNullable(RsLangItemIndex.findLangItem(project, langAttribute, crateName))
                }.orElse(null)
            }

            return StdKnownItems(absolutePathResolver, langItemsResolver)
        }
    }
}
