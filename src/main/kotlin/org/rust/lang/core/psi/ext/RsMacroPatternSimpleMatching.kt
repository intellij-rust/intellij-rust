package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsMacroPatternSimpleMatching

abstract class RsMacroPatternSimpleMatchingImplMixin(node: ASTNode) : RsNamedElementImpl(node), RsMacroPatternSimpleMatching {

    override fun getNameIdentifier(): PsiElement? = macroBinding
}
