package org.rust.lang.core.resolve.scope

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustExprPath
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.util.match

public interface RustResolveScope : RustCompositeElement {

    public fun lookup(path: RustExprPath): RustNamedElement? {
        for (c in getChildren()) {
            if (c is RustNamedElement && path.getIdentifier().match(c.getName())) {
                return path.getExprPath()?.let {
                    subPath ->
                        when (c) {
                            is RustResolveScope -> c.lookup(subPath)
                            else -> null
                        }
                } ?: c
            }
        }

        return null;
    }

}


