/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.util.PsiTreeUtil
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.*
import org.rust.lang.core.stubs.RsImplItemStub
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.lang.core.types.type

abstract class RsImplItemImplMixin : RsStubbedElementImpl<RsImplItemStub>, RsImplItem {

    constructor(node: ASTNode) : super(node)
    constructor(stub: RsImplItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int) = RsIcons.IMPL

    override val isPublic: Boolean get() = false // pub does not affect imls at all

    override fun getPresentation(): ItemPresentation {
        val t = typeReference?.typeElement
        if (t is RsBaseType) {
            val pres = (t.path?.reference?.resolve() as? RsNamedElement)?.presentation
            if (pres != null) {
                return PresentationData(pres.presentableText, pres.locationString, RsIcons.IMPL, null)
            }
        }
        return PresentationData(typeReference?.text ?: "Impl", null, RsIcons.IMPL, null)
    }

    override val inheritedFunctions: List<BoundElement<RsFunction>> get() {
        val trait = implementedTrait ?: return emptyList()
        val directlyImplemented = functionList.map { it.name }.toSet()
        return trait.element.functionList.filter {
            it.name !in directlyImplemented
        }.map {
            BoundElement(it, trait.subst)
        }
    }

    override val implementedTrait: BoundElement<RsTraitItem>? get() {
        val (trait, subst) = traitRef?.resolveToBoundTrait ?: return null
        val aliases = typeAliasList.mapNotNull { typeAlias ->
            typeAlias.name?.let { TyTypeParameter(trait, it) to typeAlias.type }
        }.toMap()
        return BoundElement(trait, subst + aliases)
    }

    override val innerAttrList: List<RsInnerAttr>
        get() = PsiTreeUtil.getStubChildrenOfTypeAsList(this, RsInnerAttr::class.java)

    override val outerAttrList: List<RsOuterAttr>
        get() = PsiTreeUtil.getStubChildrenOfTypeAsList(this, RsOuterAttr::class.java)
}

/**
 * @return pair of two lists: (mandatory trait members, optional trait members)
 */
fun RsImplItem.toImplementOverride(resolvedTrait: RsTraitItem? = null): Pair<List<RsNamedElement>, List<RsNamedElement>>? {
    val trait = resolvedTrait ?: traitRef?.resolveToTrait ?: return null
    val traitMembers = trait.children.filterIsInstance<RsAbstractable>()
    val members = children.filterIsInstance<RsAbstractable>()
    val canImplement = traitMembers.associateBy { it.name }
    val mustImplement = canImplement.filterValues { it.isAbstract }
    val implemented = members.associateBy { it.name }
    val notImplemented = mustImplement.keys - implemented.keys
    val toImplement = traitMembers.filter { it.name in notImplemented }

    return toImplement to traitMembers
}
