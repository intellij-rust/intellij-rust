package org.rust.lang.core.resolve.util

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustModItem
import org.rust.lang.core.psi.impl.RustFileImpl
import org.rust.lang.core.psi.util.*
import org.rust.lang.core.resolve.scope.RustResolveScope

public object RustResolveUtil {

    fun getResolveScopeFor(elem: PsiElement): RustResolveScope? {
        var current = elem.parent
        while (current != null) {
            when (current) {
                is RustResolveScope -> return current
                else                -> current = current.parent
            }
        }

        return null
    }

    /**
     *  Returns a "crate root": the top level module of the crate to which the `PsiElement` belongs.
     *
     *  In most cases it will be situated in another file, typically `src/main.rs` or `src/lib.rs`.
     *
     *  Reference:
     *    https://doc.rust-lang.org/reference.html#crates-and-source-files
     */
    fun getCrateRootFor(elem: PsiElement): RustModItem? {
        val mod = elem as RustModItem? ?: elem.containingMod ?: return null

        val superMod = mod.`super`
        return if (superMod == null) mod else getCrateRootFor(superMod)
    }
}
