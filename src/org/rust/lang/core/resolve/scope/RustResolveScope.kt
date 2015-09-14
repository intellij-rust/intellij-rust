package org.rust.lang.core.resolve.scope

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustPathExpr
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustPathExprPart
import org.rust.lang.core.psi.util.match

public interface RustResolveScope : RustCompositeElement {

    public fun lookup(path: RustPathExpr): RustNamedElement? {
        // TODO(kudinkin): fix for global- & self-refs
        return path.getPathExprPart().let { part -> lookup(part) }
    }

    public fun lookup(pathPart: RustPathExprPart): RustNamedElement? {
        for (c in getChildren()) {
            if (c is RustNamedElement && pathPart.getIdentifier().match(c.getName())) {
                return pathPart.getPathExprPart().let {
                    tail ->
                    when (c) {
                        is RustResolveScope -> c.lookup(tail)
                        else -> null
                    }
                } ?: c
            }
        }

        return null;
    }

}


