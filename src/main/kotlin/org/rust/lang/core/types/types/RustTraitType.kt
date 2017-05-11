package org.rust.lang.core.types.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.types.Ty


/**
 * A "trait object" type should not be confused with a trait.
 * Though you use the same path to denote both traits and trait objects,
 * only the latter are types.
 */
data class RustTraitType(val trait: RsTraitItem) : Ty {

    override fun canUnifyWith(other: Ty, project: Project): Boolean =
        other is RsTraitItem && trait == other.trait

    override fun toString(): String = trait.name ?: "<anonymous>"
}
