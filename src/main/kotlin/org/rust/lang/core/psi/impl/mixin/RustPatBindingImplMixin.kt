package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.RustPatBindingElement
import org.rust.lang.core.psi.RustPatElement
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import javax.swing.Icon

abstract class RustPatBindingImplMixin(node: ASTNode) : RustNamedElementImpl(node)
                                                      , RustPatBindingElement {

    override fun getNavigationElement(): PsiElement = identifier

    override fun getIcon(flags: Int): Icon? {
        return RustIcons.BINDING
    }
}

val RustPatBindingElement.isMut: Boolean
    get() = bindingMode?.mut != null

val RustPatBindingElement.rootPattern: RustPatElement?
    get() = PsiTreeUtil.getTopmostParentOfType(this, RustPatElement::class.java)
