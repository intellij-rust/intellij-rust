package org.rust.lang.core.psi.visitors

import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.RustLiteral

open class RustVisitorEx : RsVisitor() {

    open fun visitNumericLiteral(literal: RustLiteral.Number) = visitElement(literal)

    open fun visitTextLiteral(literal: RustLiteral.Text) = visitElement(literal)

}
