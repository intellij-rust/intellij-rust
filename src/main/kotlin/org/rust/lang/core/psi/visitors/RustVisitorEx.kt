package org.rust.lang.core.psi.visitors

import org.rust.lang.core.psi.RustLiteral
import org.rust.lang.core.psi.RustVisitor

open class RustVisitorEx : RustVisitor() {
    open fun visitLiteral(literal: RustLiteral) = visitElement(literal)
    open fun visitNumericLiteral(literal: RustLiteral.Number) = visitLiteral(literal)
    open fun visitTextLiteral(literal: RustLiteral.Text) = visitLiteral(literal)
}
