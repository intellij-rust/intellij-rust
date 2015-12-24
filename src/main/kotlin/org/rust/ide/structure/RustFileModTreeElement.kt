package org.rust.ide.structure

import org.rust.lang.core.psi.RustFileModItem

class RustFileModTreeElement(item: RustFileModItem) : RustModTreeElement(item) {

    override fun getPresentableText(): String? = element?.name

}
