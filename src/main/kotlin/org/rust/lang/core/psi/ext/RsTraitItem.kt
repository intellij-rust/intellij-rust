/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.Condition
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Query
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.macros.ExpansionResult
import org.rust.lang.core.psi.*
import org.rust.lang.core.resolve.STD_DERIVABLE_TRAITS
import org.rust.lang.core.stubs.RsTraitItemStub
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.RsPsiTypeImplUtil
import org.rust.lang.core.types.ty.Ty
import org.rust.openapiext.filterIsInstanceQuery
import org.rust.openapiext.filterQuery
import org.rust.openapiext.mapQuery
import javax.swing.Icon

val RsTraitItem.langAttribute: String? get() = queryAttributes.langAttribute

val RsTraitItem.isSizedTrait: Boolean get() = langAttribute == "sized"

val RsTraitItem.isStdDerivable: Boolean get() {
    val derivableTrait = STD_DERIVABLE_TRAITS[name] ?: return false
    return containingCargoPackage?.origin == PackageOrigin.STDLIB &&
        containingMod.modName == derivableTrait.modName
}

val BoundElement<RsTraitItem>.flattenHierarchy: Collection<BoundElement<RsTraitItem>> get() {
    val result = mutableListOf<BoundElement<RsTraitItem>>()
    val visited = mutableSetOf<RsTraitItem>()
    fun dfs(boundTrait: BoundElement<RsTraitItem>) {
        if (!visited.add(boundTrait.element)) return
        result += boundTrait
        boundTrait.element.superTraits.forEach { dfs(it.substitute(boundTrait.subst)) }
    }
    dfs(this)

    return result
}

val BoundElement<RsTraitItem>.associatedTypesTransitively: Collection<RsTypeAlias>
    get() = flattenHierarchy.flatMap { it.element.members?.typeAliasList.orEmpty() }

fun RsTraitItem.searchForImplementations(): Query<RsImplItem> {
    return ReferencesSearch.search(this, this.useScope)
        .mapQuery { it.element.parent?.parent }
        .filterIsInstanceQuery<RsImplItem>()
        .filterQuery(Condition { it.typeReference != null })
}

private val RsTraitItem.superTraits: Sequence<BoundElement<RsTraitItem>> get() {
    val bounds = typeParamBounds?.polyboundList.orEmpty().asSequence()
    return bounds.mapNotNull { it.bound.traitRef?.resolveToBoundTrait }
}

abstract class RsTraitItemImplMixin : RsStubbedNamedElementImpl<RsTraitItemStub>, RsTraitItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsTraitItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val innerAttrList: List<RsInnerAttr>
        get() = PsiTreeUtil.getStubChildrenOfTypeAsList(this, RsInnerAttr::class.java)

    override val outerAttrList: List<RsOuterAttr>
        get() = PsiTreeUtil.getStubChildrenOfTypeAsList(this, RsOuterAttr::class.java)

    override fun getIcon(flags: Int): Icon =
        iconWithVisibility(flags, RsIcons.TRAIT)

    override val isPublic: Boolean get() = RsPsiImplUtil.isPublic(this, stub)

    override val crateRelativePath: String? get() = RsPsiImplUtil.crateRelativePath(this)

    override val implementedTrait: BoundElement<RsTraitItem>? get() = BoundElement(this)

    override val associatedTypesTransitively: Collection<RsTypeAlias>
        get() = BoundElement(this).associatedTypesTransitively

    override val isUnsafe: Boolean get() {
        val stub = stub
        return stub?.isUnsafe ?: (unsafe != null)
    }

    override val declaredType: Ty get() = RsPsiTypeImplUtil.declaredType(this)

    override fun getContext() = ExpansionResult.getContextImpl(this)
}


class TraitImplementationInfo private constructor(
    val trait: RsTraitItem,
    val traitName: String,
    traitMembers: RsMembers,
    implMembers: RsMembers,
    // Macros can add methods
    hasMacros: Boolean
) {
    val declared = traitMembers.abstractable()
    private val implemented = implMembers.abstractable()
    private val declaredByName = declared.associateBy { it.name!! }
    private val implementedByName = implemented.associateBy { it.name!! }


    val missingImplementations: List<RsAbstractable> = if (!hasMacros)
        declared.filter { it.isAbstract }.filter { it.name !in implementedByName }
    else emptyList()

    val alreadyImplemented: List<RsAbstractable> =  if (!hasMacros)
        declared.filter { it.isAbstract }.filter { it.name in implementedByName }
    else emptyList()

    val nonExistentInTrait: List<RsAbstractable> = implemented.filter { it.name !in declaredByName }

    val implementationToDeclaration: List<Pair<RsAbstractable, RsAbstractable>> =
        implemented.mapNotNull { imp ->
            val dec = declaredByName[imp.name]
            if (dec != null) imp to dec else null
        }


    private fun RsMembers.abstractable(): List<RsAbstractable> =
        PsiTreeUtil.getStubChildrenOfTypeAsList(this, RsAbstractable::class.java)
            .filter { it.name != null }

    companion object {
        fun create(trait: RsTraitItem, impl: RsImplItem): TraitImplementationInfo? {
            val traitName = trait.name ?: return null
            val traitMembers = trait.members ?: return null
            val implMembers = impl.members ?: return null
            val hasMacros = implMembers.macroCallList.isNotEmpty()
            return TraitImplementationInfo(trait, traitName, traitMembers, implMembers, hasMacros)
        }
    }
}
