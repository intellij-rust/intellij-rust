package org.rust.lang.core.psi

import com.intellij.psi.PsiDirectory
import org.rust.lang.core.names.RustQualifiedName

interface RustMod : RustNamedElement, RustItemsOwner {
    /**
     *  Returns a parent module (`super::` in paths).
     *
     *  The parent module may be in the same or other file.
     *
     *  Reference:
     *    https://doc.rust-lang.org/reference.html#paths
     */
    val `super`: RustMod?

    val ownsDirectory: Boolean

    val ownedDirectory: PsiDirectory?

    val isCrateRoot: Boolean

    val isTopLevelInFile: Boolean

    val canonicalNameInFile: RustQualifiedName?

    companion object {
        val MOD_RS = "mod.rs"
    }
}
