package org.rust.lang.core.resolve

import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.queryAttributes
import org.rust.lang.core.psi.ext.resolveToTrait

val RsTraitItem.langAttribute: String? get() {
    if (this.stub != null) return this.stub.langAttribute
    return this.queryAttributes.langAttribute
}

val RsImplItem.langAttribute: String? get() {
    return this.traitRef?.resolveToTrait?.langAttribute
}

val RsImplItem.isDerefTrait: Boolean get() {
    return this.langAttribute == "deref"
}
