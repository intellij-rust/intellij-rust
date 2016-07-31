package org.rust.lang.core.types

import com.intellij.codeInsight.completion.CompletionUtil
import org.rust.lang.core.psi.RustStructItemElement
import org.rust.lang.core.types.visitors.RustTypeVisitor

class RustStructType(struct: RustStructItemElement) : RustStructOrEnumTypeBase() {

    override val item = CompletionUtil.getOriginalOrSelf(struct)

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitStruct(this)

    override fun toString(): String = item.name ?: "<anonymous>"
}
