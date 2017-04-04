package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsLabelDecl

interface RsLabeledExpression : RsCompositeElement {
    val labelDecl: RsLabelDecl?
}
