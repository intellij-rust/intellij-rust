package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsMacro
import org.rust.lang.core.psi.RsPsiFactory

abstract class RsMacroImplMixin(node: ASTNode): RsCompositeElementImpl(node), RsMacro {
    val expansion: RsFile?

    init {
        expansion = if (isErrorChainInvocation) {
            val expansionFile = RsPsiFactory(project).createErrorChainExpansion()
            expansionFile.children
                .filterIsInstance<RsCompositeElement>()
                .forEach { it.setContext(parent.parent) }
            expansionFile
        } else {
            null
        }
    }
}

val RsMacro.expansion: RsFile? get() = (this as? RsMacroImplMixin)?.expansion

private val RsMacro.isErrorChainInvocation: Boolean get() {
    if (macroInvocation.referenceName != "error_chain") return false
    val lbrace = macroArg?.firstChild ?: return false
    if (lbrace.node.elementType != RsElementTypes.LBRACE) return false
    val rbrace = PsiTreeUtil.nextVisibleLeaf(lbrace) ?: return false
    return rbrace.node.elementType == RsElementTypes.RBRACE
}
