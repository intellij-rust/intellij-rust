package org.rust.lang.core.resolve.scope

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustPathExpr
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.util.match
import org.rust.lang.core.resolve.RustResolveEngine

public interface RustResolveScope : RustCompositeElement {

//    public fun lookup(pathPart: RustPathExprPart): RustNamedElement? {
//        for (c in getChildren()) {
//            if (c is RustNamedElement && pathPart.getIdentifier().match(c.getName())) {
//                return pathPart.getPathExprPart().let {
//                    tail ->
//                        when (tail) {
//                            null -> return c
//                            else -> when (c) {
//                                        is RustResolveScope -> c.lookup(tail)
//                                        else -> null
//                                    }
//                        }
//                }
//            }
//        }
//
//        return null;
//    }

}

//
// Extension points
//

fun RustResolveScope.resolveWith(v: RustResolveEngine.ResolveScopeVisitor): RustNamedElement? {
    this.accept(v)
    return v.matched
}
