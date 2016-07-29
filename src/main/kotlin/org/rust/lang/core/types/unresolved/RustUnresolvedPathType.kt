package org.rust.lang.core.types.unresolved

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustQualifiedReferenceElement
import org.rust.lang.core.symbols.RustQualifiedPath
import org.rust.lang.core.types.visitors.RustUnresolvedTypeVisitor


class RustUnresolvedPathType(val path: RustQualifiedPath) : RustUnresolvedTypeBase() {

    constructor(path: RustQualifiedReferenceElement) : this(path.decay)

    init {
        check(path !is PsiElement)
    }

    override fun <T> accept(visitor: RustUnresolvedTypeVisitor<T>): T = visitor.visitPathType(this)

    override fun toString(): String = "[U] $path"
}

val RustQualifiedReferenceElement.decay: RustQualifiedPath
    get() = RustQualifiedPath.create(part, qualifier?.decay, fullyQualified = fullyQualified)
