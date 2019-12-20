/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.stubs.IStubElementType
import com.intellij.util.Query
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.resolve.KNOWN_DERIVABLE_TRAITS
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.stubs.RsTraitItemStub
import org.rust.lang.core.types.*
import org.rust.lang.core.types.infer.substitute
import org.rust.lang.core.types.regions.ReEarlyBound
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.openapiext.filterIsInstanceQuery
import org.rust.openapiext.filterQuery
import org.rust.openapiext.mapQuery
import javax.swing.Icon

private val LOG: Logger = Logger.getInstance(RsTraitItem::class.java)

val RsTraitItem.langAttribute: String? get() = queryAttributes.langAttribute

val RsTraitItem.isSizedTrait: Boolean get() = langAttribute == "sized"

val RsTraitItem.isAuto: Boolean
    get() = greenStub?.isAuto ?: (node.findChildByType(RsElementTypes.AUTO) != null)

val RsTraitItem.isKnownDerivable: Boolean get() {
    val derivableTrait = KNOWN_DERIVABLE_TRAITS[name] ?: return false
    return derivableTrait.findTrait(knownItems) == this
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

fun RsTraitItem.findAssociatedType(name: String): RsTypeAlias? =
    associatedTypesTransitively.find { it.name == name }

fun RsTraitItem.substAssocType(assocName: String, ty: Ty?): BoundElement<RsTraitItem> =
    BoundElement(this).substAssocType(assocName, ty)

fun BoundElement<RsTraitItem>.substAssocType(assocName: String, ty: Ty?): BoundElement<RsTraitItem> {
    val assocType = element.findAssociatedType(assocName)
    val assoc = if (assocType != null && ty != null) assoc + (assocType to ty) else assoc
    return BoundElement(element, subst, assoc)
}

fun RsTraitItem.searchForImplementations(): Query<RsImplItem> {
    return ReferencesSearch.search(this, this.useScope)
        .mapQuery { it.element.parent?.parent }
        .filterIsInstanceQuery<RsImplItem>()
        .filterQuery(Condition { it.typeReference != null })
}

private val RsTraitItem.superTraits: Sequence<BoundElement<RsTraitItem>> get() {
    // trait Foo where Self: Bar {}
    val whereBounds = whereClause?.wherePredList.orEmpty().asSequence()
        .filter { (it.typeReference?.typeElement as? RsBaseType)?.path?.hasCself == true }
        .flatMap { it.typeParamBounds?.polyboundList.orEmpty().asSequence() }
    // trait Foo: Bar {}
    val bounds = typeParamBounds?.polyboundList.orEmpty().asSequence() + whereBounds
    return bounds
        .filter { !it.hasQ } // ignore `?Sized`
        .mapNotNull { it.bound.traitRef?.resolveToBoundTrait() }
}

val RsTraitItem.isSized: Boolean get() {
    return implementedTrait?.flattenHierarchy.orEmpty().any { it.element.isSizedTrait }
}

fun RsTraitItem.withSubst(vararg subst: Ty): BoundElement<RsTraitItem> {
    val typeParameterList = typeParameters
    val substitution = if (typeParameterList.size != subst.size) {
        LOG.warn("Trait has ${typeParameterList.size} type parameters but received ${subst.size} types for substitution")
        emptySubstitution
    } else {
        typeParameterList.withIndex().associate { (i, par) ->
            val param = TyTypeParameter.named(par)
            param to (subst.getOrNull(i) ?: param)
        }.toTypeSubst()
    }
    return BoundElement(this, substitution)
}

fun RsTraitItem.withDefaultSubst(): BoundElement<RsTraitItem> =
    BoundElement(this, defaultSubstitution(this))

private fun defaultSubstitution(item: RsTraitItem): Substitution {
    val typeSubst = item.typeParameters.associate {
        val parameter = TyTypeParameter.named(it)
        parameter to parameter
    }
    val regionSubst = item.lifetimeParameters.associate {
        val parameter = ReEarlyBound(it)
        parameter to parameter
    }
    return Substitution(typeSubst, regionSubst)
}

abstract class RsTraitItemImplMixin : RsStubbedNamedElementImpl<RsTraitItemStub>, RsTraitItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsTraitItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon =
        iconWithVisibility(flags, RsIcons.TRAIT)

    override val crateRelativePath: String? get() = RsPsiImplUtil.crateRelativePath(this)

    override val implementedTrait: BoundElement<RsTraitItem>? get() = BoundElement(this)

    override val associatedTypesTransitively: Collection<RsTypeAlias>
        get() = BoundElement(this).associatedTypesTransitively

    override val isUnsafe: Boolean get() {
        val stub = greenStub
        return stub?.isUnsafe ?: (unsafe != null)
    }

    override val declaredType: Ty get() = RsPsiTypeImplUtil.declaredType(this)

    override fun getContext(): PsiElement? = RsExpandedElement.getContextImpl(this)

    override fun getUseScope(): SearchScope = RsPsiImplUtil.getDeclarationUseScope(this) ?: super.getUseScope()
}


class TraitImplementationInfo private constructor(
    val trait: RsTraitItem,
    val traitName: String,
    traitMembers: RsMembers,
    implMembers: RsMembers
) {
    val declared = traitMembers.abstractable()
    private val implemented = implMembers.abstractable()
    private val declaredByName = declared.associateBy { it.name!! }
    private val implementedByNameAndType = implemented.associateBy { it.name!! to it.elementType }


    val missingImplementations: List<RsAbstractable> =
        declared.filter { it.isAbstract }.filter { it.name to it.elementType !in implementedByNameAndType }

    val alreadyImplemented: List<RsAbstractable> =
        declared.filter { it.isAbstract }.filter { it.name to it.elementType in implementedByNameAndType }

    val nonExistentInTrait: List<RsAbstractable> = implemented.filter { it.name !in declaredByName }

    val implementationToDeclaration: List<Pair<RsAbstractable, RsAbstractable>> =
        implemented.mapNotNull { imp ->
            val dec = declaredByName[imp.name]
            if (dec != null) imp to dec else null
        }


    private fun RsMembers.abstractable(): List<RsAbstractable> =
        expandedMembers.filter { it.name != null }

    companion object {
        fun create(trait: RsTraitItem, impl: RsImplItem): TraitImplementationInfo? {
            val traitName = trait.name ?: return null
            val traitMembers = trait.members ?: return null
            val implMembers = impl.members ?: return null
            return TraitImplementationInfo(trait, traitName, traitMembers, implMembers)
        }
    }
}
