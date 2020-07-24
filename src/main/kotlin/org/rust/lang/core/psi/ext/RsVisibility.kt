/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.impl.ElementBase
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.PlatformIcons
import org.rust.lang.core.psi.*
import javax.swing.Icon

interface RsVisible : RsElement {
    val visibility: RsVisibility
    val isPublic: Boolean  // restricted visibility considered as public
}

interface RsVisibilityOwner : RsVisible {
    @JvmDefault
    val vis: RsVis?
        get() = PsiTreeUtil.getStubChildOfType(this, RsVis::class.java)

    @JvmDefault
    override val visibility: RsVisibility
        get() = vis?.visibility ?: RsVisibility.Private

    @JvmDefault
    override val isPublic: Boolean
        get() = vis != null
}

fun RsVisibilityOwner.iconWithVisibility(flags: Int, icon: Icon): Icon {
    val visibilityIcon = when (vis?.stubKind) {
        RsVisStubKind.PUB -> PlatformIcons.PUBLIC_ICON
        RsVisStubKind.CRATE, RsVisStubKind.RESTRICTED -> PlatformIcons.PROTECTED_ICON
        null -> PlatformIcons.PRIVATE_ICON
    }
    return ElementBase.iconWithVisibilityIfNeeded(flags, icon, visibilityIcon)
}

fun RsVisible.isVisibleFrom(mod: RsMod): Boolean {
    // XXX: this hack fixes false-positive "E0603 module is private" for modules with multiple
    // declarations. It produces false-negatives, see
    if (this is RsFile && declarations.size > 1) return true

    val elementMod = when (val visibility = visibility) {
        RsVisibility.Public -> return true
        RsVisibility.Private -> (if (this is RsMod) this.`super` else containingMod) ?: return true
        is RsVisibility.Restricted -> visibility.inMod
    }

    // We have access to any item in any super module of `mod`
    // Note: `mod.superMods` contains `mod`
    if (mod.superMods.contains(elementMod)) return true
    if (mod is RsFile && mod.originalFile == elementMod) return true

    // Enum variants in a pub enum are public by default
    if (this is RsNamedFieldDecl && parent.parent is RsEnumVariant) return true

    val members = this.context as? RsMembers ?: return false
    val parent = members.context ?: return true
    return when {
        // Associated items in a pub Trait are public by default
        parent is RsImplItem && parent.traitRef != null -> {
            parent.traitRef?.resolveToTrait()?.isVisibleFrom(mod) ?: true
        }
        parent is RsTraitItem -> parent.isVisibleFrom(mod)
        else -> false
    }
}

enum class RsVisStubKind {
    PUB, CRATE, RESTRICTED
}

val RsVis.stubKind: RsVisStubKind
    get() = greenStub?.kind ?: when {
        crate != null -> RsVisStubKind.CRATE
        visRestriction != null -> RsVisStubKind.RESTRICTED
        else -> RsVisStubKind.PUB
    }

sealed class RsVisibility {
    object Private : RsVisibility()
    object Public : RsVisibility()
    data class Restricted(val inMod: RsMod) : RsVisibility()
}

fun RsVisibility.intersect(other: RsVisibility): RsVisibility = when (this) {
    RsVisibility.Private -> this
    RsVisibility.Public -> other
    is RsVisibility.Restricted -> when (other) {
        RsVisibility.Private -> other
        RsVisibility.Public -> this
        is RsVisibility.Restricted -> {
            RsVisibility.Restricted(if (inMod.superMods.contains(other.inMod)) inMod else other.inMod)
        }
    }
}

val RsVis.visibility: RsVisibility
    get() = when (stubKind) {
        RsVisStubKind.PUB -> RsVisibility.Public
        RsVisStubKind.CRATE -> crateRoot?.let { RsVisibility.Restricted(it) } ?: RsVisibility.Public
        RsVisStubKind.RESTRICTED -> {
            val restrictedIn = visRestriction!!.path.reference?.resolve() as? RsMod
            if (restrictedIn != null) RsVisibility.Restricted(restrictedIn) else  RsVisibility.Public
        }
    }
