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

    override val implementedTrait: BoundElement<RsTraitItem>? get() {
        val (trait, subst) = traitRef?.resolveToBoundTrait ?: return null
        val aliases = members?.typeAliasList.orEmpty().mapNotNull { typeAlias ->
            trait.members?.typeAliasList.orEmpty()
                .find { it.name == typeAlias.name }
                ?.let { TyTypeParameter.associated(it) to typeAlias.type }
        }.toMap()
        return BoundElement(trait, subst + aliases)
    }

    override val innerAttrList: List<RsInnerAttr>
        get() = PsiTreeUtil.getStubChildrenOfTypeAsList(this, RsInnerAttr::class.java)

    override val outerAttrList: List<RsOuterAttr>
        get() = PsiTreeUtil.getStubChildrenOfTypeAsList(this, RsOuterAttr::class.java)

    override val associatedTypesTransitively: Collection<RsTypeAlias> get() {
        val implAliases = members?.typeAliasList.orEmpty()
        val traitAliases = implementedTrait?.associatedTypesTransitively ?: emptyList()
        return implAliases + traitAliases.filter { trAl -> implAliases.find { it.name == trAl.name } == null }
    }
}
