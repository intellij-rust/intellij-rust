package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustExprPath
import org.rust.lang.core.psi.impl.RustCompositeElementImpl
import org.rust.lang.core.resolve.ref.RustReference

public abstract class RustExprPathImplMixin(node: ASTNode) : RustCompositeElementImpl(node), RustExprPath {

    val reference = RustReference(this)

    override fun getReference(): PsiReference? = reference
}

