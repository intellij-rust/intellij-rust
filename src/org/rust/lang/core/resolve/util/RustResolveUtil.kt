package org.rust.lang.core.resolve.util

import com.intellij.psi.PsiElement
import org.rust.lang.core.resolve.scope.RustResolveScope

public object RustResolveUtil {

    fun getResolveScopeFor(element: PsiElement): RustResolveScope {
        var current = element
        while (true) {
            when (current) {
                is RustResolveScope -> return current
                else -> current = current.getParent()
            }
        }
    }

}
