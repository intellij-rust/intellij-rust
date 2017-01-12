package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.RsValueParameter
import org.rust.lang.core.psi.impl.RustNamedElementImpl

abstract class RustPatBindingImplMixin(node: ASTNode) : RustNamedElementImpl(node),
                                                        RsPatBinding {

    override fun getNavigationElement(): PsiElement = identifier

    override fun getIcon(flags: Int) = when {
        isArg && isMut -> RustIcons.MUT_ARGUMENT
        isArg -> RustIcons.ARGUMENT
        isMut -> RustIcons.MUT_BINDING
        else -> RustIcons.BINDING
    }

    override fun getUseScope(): SearchScope {
        val owner = PsiTreeUtil.getParentOfType(this,
            RsBlock::class.java,
            RsFunction::class.java
        )

        if (owner != null) return LocalSearchScope(owner)

        return super.getUseScope()
    }
}

val RsPatBinding.isMut: Boolean
    get() = bindingMode?.mut != null

val RsPatBinding.isArg: Boolean
    get() = parent?.parent is RsValueParameter
