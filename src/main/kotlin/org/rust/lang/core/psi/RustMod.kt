package org.rust.lang.core.psi

import com.intellij.psi.PsiDirectory
import org.rust.lang.core.symbols.RustQualifiedPath
import org.rust.lang.core.symbols.RustQualifiedPathPart
import java.util.*

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

    companion object {
        val MOD_RS = "mod.rs"
    }
}

val RustMod.superMods: List<RustMod> get() {
    // For malformed programs, chain of `super`s may be infinite
    // because of cycles, and we need to detect this situation.
    val visited = HashSet<RustMod>()
    return generateSequence(this) { it.`super`}
        .takeWhile { visited.add(it) }
        .toList()
}

val RustMod.canonicalCratePath: RustQualifiedPath? get() =
    superMods.dropLast(1).foldRight(null, {mod, qualifier: RustQualifiedPath? -> RustQualifiedPath.create(
        RustQualifiedPathPart.from(mod.modName),
        qualifier = qualifier,
        fullyQualified = true
    )})

