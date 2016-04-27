package org.rust.lang.core.psi

import com.intellij.psi.PsiDirectory
import org.rust.lang.core.resolve.scope.RustResolveScope

interface RustMod : RustNamedElement, RustResolveScope {
    val items: List<RustItem>

    /**
     *  Returns a parent module (`super::` in paths).
     *
     *  The parent module may be in the same or other file.
     *
     *  Reference:
     *    https://doc.rust-lang.org/reference.html#paths
     */
    val `super`: RustModItem?

    val ownsDirectory: Boolean

    val ownedDirectory: PsiDirectory?

    val isCrateRoot: Boolean

    val isTopLevelInFile: Boolean

    val modDecls: Collection<RustModDeclItem>
    //  Default implementation here causes https://youtrack.jetbrains.com/issue/KT-12114
    //  get() = PsiTreeUtil.getChildrenOfTypeAsList(this, RustModDeclItem::class.java)

    override val declarations: Collection<RustDeclaringElement>
        get() = items
}
