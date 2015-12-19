package org.rust.lang.structure

import org.rust.lang.RustFileType
import org.rust.lang.core.psi.RustFileModItem

class RustFileModTreeElement(item: RustFileModItem) : RustModTreeElement(item) {

    override fun getPresentableText(): String? =
        element?.let { it.containingFile.name.removeSuffix(".${RustFileType.DEFAULTS.EXTENSION}") }

}

