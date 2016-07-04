package org.rust.lang.core.psi.visitors

import org.rust.lang.core.psi.RustElementVisitor
import org.rust.lang.core.psi.RustLiteral

open class RustVisitorEx : RustElementVisitor() {

    open fun visitNumericLiteral(literal: RustLiteral.Number) = visitElement(literal)

    open fun visitTextLiteral(literal: RustLiteral.Text) = visitElement(literal)

}
