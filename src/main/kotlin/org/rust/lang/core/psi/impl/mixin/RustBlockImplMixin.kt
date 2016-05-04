package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustCompositeElementImpl
import org.rust.lang.core.psi.util.isAfter

abstract class RustBlockImplMixin(node: ASTNode) : RustCompositeElementImpl(node)
                                                 , RustBlock {

    override val declarations: Collection<RustDeclaringElement>
        get() = stmtList.filterIsInstance<RustLetDecl>()

}

/**
 *  Let declarations visible at the `element` according to Rust scoping rules.
 *  More recent declarations come first.
 *
 *  Example:
 *
 *    ```
 *    {
 *        let x = 92; // visible
 *        let x = x;  // not visible
 *                ^ element
 *        let x = 62; // not visible
 *    }
 *    ```
 */
fun RustBlock.letDeclarationsVisibleAt(element: RustCompositeElement): Sequence<RustLetDecl> =
    stmtList.asReversed().asSequence()
        .filterIsInstance<RustLetDecl>()
        .dropWhile { it.isAfter(element) }
        // Drops at most one element
        .dropWhile { PsiTreeUtil.isAncestor(it, element, true) }
