package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustPathElement
import org.rust.lang.core.psi.RustUseGlobElement
import org.rust.lang.core.psi.RustUseItemElement
import org.rust.lang.core.psi.impl.RustCompositeElementImpl
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.ref.RustReference
import org.rust.lang.core.resolve.ref.RustUseGlobReferenceImpl

abstract class RustUseGlobImplMixin(node: ASTNode) : RustCompositeElementImpl(node), RustUseGlobElement {
    override fun getReference(): RustReference =
        RustUseGlobReferenceImpl(this)

    override val referenceNameElement: PsiElement
        get() = requireNotNull(identifier ?: self) {
            "Use glob must have an identifier: $this ${this.text} at ${this.containingFile.virtualFile.path}"
        }
}

val RustUseGlobElement.basePath: RustPathElement?
    get() = parentOfType<RustUseItemElement>()?.path

val RustUseGlobElement.isSelf: Boolean get() = self != null
