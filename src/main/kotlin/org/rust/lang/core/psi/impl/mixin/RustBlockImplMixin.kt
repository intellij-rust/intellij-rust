package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import org.rust.lang.core.psi.RustBlockElement
import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustDeclaringElement
import org.rust.lang.core.psi.RustLetDeclElement
import org.rust.lang.core.psi.impl.RustCompositeElementImpl

abstract class RustBlockImplMixin(node: ASTNode) : RustCompositeElementImpl(node)
                                                 , RustBlockElement {

    override val declarations: Collection<RustDeclaringElement>
        get() = stmtList.filterIsInstance<RustLetDeclElement>()

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
fun RustBlockElement.letDeclarationsVisibleAt(element: RustCompositeElement): Sequence<RustLetDeclElement> =
    stmtList.asReversed().asSequence()
        .filterIsInstance<RustLetDeclElement>()
        .dropWhile { PsiUtilCore.compareElementsByPosition(element, it) < 0 }
        // Drops at most one element
        .dropWhile { PsiTreeUtil.isAncestor(it, element, true) }
