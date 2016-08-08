package org.rust.lang.core.psi

import com.intellij.psi.PsiDirectory
import org.rust.lang.core.symbols.RustQualifiedPath
import org.rust.lang.core.symbols.RustQualifiedPathPart

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

    val modName: String?

    val ownsDirectory: Boolean

    val ownedDirectory: PsiDirectory?

    val isCrateRoot: Boolean

    val isTopLevelInFile: Boolean

    companion object {
        val MOD_RS = "mod.rs"
    }
}

val RustMod.canonicalCratePath: RustQualifiedPath?
    get() {
        if (isCrateRoot)
            return null

        return RustQualifiedPath.create(
            RustQualifiedPathPart.from(modName),
            qualifier = `super`?.canonicalCratePath,
            fullyQualified = true
        )
    }
