package org.rust.lang.core.psi

open class RustVisitorEx : RustVisitor() {
    open fun visitLiteral(literal: RustLiteral) = visitElement(literal)
    open fun visitNumericLiteral(literal: RustLiteral.Number) = visitLiteral(literal)
    open fun visitTextLiteral(literal: RustLiteral.Text) = visitLiteral(literal)
}
