package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.RustBlockElement
import org.rust.lang.core.psi.RustFnElement
import org.rust.lang.core.psi.RustPatBindingElement
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import org.rust.lang.core.psi.impl.RustParameterElementImpl

abstract class RustPatBindingImplMixin(node: ASTNode) : RustNamedElementImpl(node),
                                                        RustPatBindingElement {

    override fun getNavigationElement(): PsiElement = identifier

    override fun getIcon(flags: Int) = when {
        isArg && isMut -> RustIcons.MUT_ARGUMENT
        isArg -> RustIcons.ARGUMENT
        isMut -> RustIcons.MUT_BINDING
        else -> RustIcons.BINDING
    }

    override fun getUseScope(): SearchScope {
        val owner = PsiTreeUtil.getParentOfType(this,
            RustBlockElement::class.java,
            RustFnElement::class.java
        )

        if (owner != null) return LocalSearchScope(owner)

        return super.getUseScope()
    }
}

val RustPatBindingElement.isMut: Boolean
    get() = bindingMode?.mut != null

val RustPatBindingElement.isArg: Boolean
    get() = parent?.parent is RustParameterElementImpl
