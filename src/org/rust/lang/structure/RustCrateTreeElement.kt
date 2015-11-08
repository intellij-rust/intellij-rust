package org.rust.lang.structure

import org.rust.lang.RustFileType
import org.rust.lang.core.psi.RustCrateItem

class RustCrateTreeElement(item: RustCrateItem) : RustModTreeElement(item) {

    override fun getPresentableText(): String? =
        element?.let { it.containingFile.name.removeSuffix(".${RustFileType.DEFAULTS.EXTENSION}") }

}

