package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.RustForeignModItemElement
import org.rust.lang.core.psi.RustOuterAttrElement
import org.rust.lang.core.psi.impl.RustCompositeElementImpl

abstract class RustForeignModItemImplMixin(node: ASTNode) : RustCompositeElementImpl(node), RustForeignModItemElement {
    override val outerAttrList: List<RustOuterAttrElement>
        get() = PsiTreeUtil.getChildrenOfTypeAsList(this, RustOuterAttrElement::class.java)

    override val isPublic: Boolean get() = false // visibility does not affect foreign mods
}
