/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiDirectory
import java.util.*

interface RsMod : RsQualifiedNamedElement, RsItemsOwner, RsVisible {
    /**
     *  Returns a parent module (`super::` in paths).
     *
     *  The parent module may be in the same or other file.
     *
     *  Reference:
     *    https://doc.rust-lang.org/reference.html#paths
     */
    val `super`: RsMod?

    /**
     * XXX: this might be different then [com.intellij.psi.PsiNamedElement.getName].
     *
     * This contortion is required because [org.rust.lang.core.psi.RsFile] is
     * [RsMod], but we shouldn't override its name.
     */
    val modName: String?

    val ownsDirectory: Boolean

    val ownedDirectory: PsiDirectory?

    val isCrateRoot: Boolean

    companion object {
        val MOD_RS = "mod.rs"
    }
}

val RsMod.superMods: List<RsMod> get() {
    // For malformed programs, chain of `super`s may be infinite
    // because of cycles, and we need to detect this situation.
    val visited = HashSet<RsMod>()
    return generateSequence(this) { it.`super` }
        .takeWhile { visited.add(it) }
        .toList()
}
