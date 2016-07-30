package org.rust.lang.core.psi

import com.intellij.psi.PsiDirectory

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

/**
 * Returns a string representing a path to this module from the crate root, like `foo::bar::baz`.
 * There is no leading `::`. Returns empty string for the crate root.
 *
 * This name is not guaranteed to be unique: modules from different crates
 * can have the same path within the crate.
 */
val RustMod.canonicalCratePath: String? get() {
    if (isCrateRoot) return ""
    val parentPath = `super`?.canonicalCratePath ?: return null
    return if (parentPath == "") modName else "$parentPath::$modName"
}
