package org.rust.lang.core.psi.visitors

import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.RsLiteral

open class RustVisitorEx : RsVisitor() {

    open fun visitNumericLiteral(literal: RsLiteral.Number) = visitElement(literal)

    open fun visitTextLiteral(literal: RsLiteral.Text) = visitElement(literal)

}
