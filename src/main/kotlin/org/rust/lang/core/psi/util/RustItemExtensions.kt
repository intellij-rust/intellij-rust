package org.rust.lang.core.psi.util

import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTraitRef

/**
 *  `RustItemElement` related extensions
 */


val RsTraitRef.trait: RsTraitItem?
    get() = path.reference.resolve() as? RsTraitItem
