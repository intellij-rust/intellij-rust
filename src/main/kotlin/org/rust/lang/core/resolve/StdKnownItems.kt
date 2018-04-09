/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.RsTypeDeclarationElement
import org.rust.lang.core.psi.ext.cargoWorkspace
import org.rust.lang.core.types.infer.substitute
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.ty.getTypeParameter
import org.rust.openapiext.ProjectCache
import java.util.*

class StdKnownItems private constructor(private val absolutePathResolver: (String, String) -> RsNamedElement?) {

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
        return ty.substitute(mapOf(typeParameter to elementTy))
    }

    fun findRangeTy(rangeName: String, indexType: Ty?): Ty {
        val ty = findStdTy("core", "ops::" + rangeName)

        if (indexType == null) return ty

        val typeParameter = ty.getTypeParameter("Idx") ?: return ty
        return ty.substitute(mapOf(typeParameter to indexType))
    }

    fun findStringTy(): Ty =
        findStdTy("alloc", "string::String")

    fun findArgumentsTy(): Ty =
        findCoreTy("fmt::Arguments")

    fun findOptionItem(): RsNamedElement? =
        findCoreItem("option::Option")

    fun findResultItem(): RsNamedElement? =
        findCoreItem("result::Result")

    fun findCloneTrait(): RsTraitItem? =
        findCoreItem("clone::Clone") as? RsTraitItem

    fun findEqTrait(): RsTraitItem? =
        findCoreItem("cmp::Eq") as? RsTraitItem

    fun findOrdTrait(): RsTraitItem? =
        findCoreItem("cmp::Ord") as? RsTraitItem

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

    companion object {
        private val stdKnownItemsCache =
            ProjectCache<Pair<CargoWorkspace, String>, Optional<RsNamedElement>>("stdKnownItemsCache")

        fun relativeTo(psi: RsElement): StdKnownItems {
            val project = psi.project
            val workspace = psi.cargoWorkspace ?: return StdKnownItems { _, _ -> null }
            val crateRoot = psi.crateRoot as? RsFile ?: return StdKnownItems { _, _ -> null }
            val useStdPrefix = crateRoot.attributes == RsFile.Attributes.NONE

            return StdKnownItems { prefixNoStd, name ->
                val prefix = if (useStdPrefix) "std" else prefixNoStd
                val path = "$prefix::$name"
                val key = workspace to path
                stdKnownItemsCache.getOrPut(project, key) {
                    Optional.ofNullable(resolveStringPath(path, workspace, project)?.first)
                }.orElse(null)
            }
        }
    }
}
