package org.rust.lang.core.resolve.util

import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustMod
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.superMods

object RustResolveUtil {

    /**
     *  Returns a "crate root": the top level module of the crate to which the `PsiElement` belongs.
     *
     *  In most cases it will be situated in another file, typically `src/main.rs` or `src/lib.rs`.
     *
     *  Reference:
     *    https://doc.rust-lang.org/reference.html#crates-and-source-files
     */
    fun getCrateRootModFor(elem: RustCompositeElement): RustMod? {
        val mod = elem.containingFile as? RustFile ?: return null

        val root = mod.superMods.lastOrNull()
        return if (root != null && root.isCrateRoot)
            root
        else
            null
    }
}

