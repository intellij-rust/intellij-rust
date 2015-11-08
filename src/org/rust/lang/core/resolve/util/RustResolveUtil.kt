package org.rust.lang.core.resolve.util

import com.intellij.psi.PsiElement
import org.rust.lang.core.resolve.scope.RustResolveScope

public object RustResolveUtil {

    fun getResolveScopeFor(elem: PsiElement): RustResolveScope? {
        var current = elem.parent
        while (current != null) {
            when (current) {
                is RustResolveScope -> return current
                else -> current = current.parent
            }
        }

        return null
    }

    fun getGlobalResolveScopeFor(elem: PsiElement): RustResolveScope {
        var current = getResolveScopeFor(elem)
        while (true)
        {
            val p = current?.let { getResolveScopeFor(it) } ?: break
            current = p;
        }

        return current!!;
    }

}
