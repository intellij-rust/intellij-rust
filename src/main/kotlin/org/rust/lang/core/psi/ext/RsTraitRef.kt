package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTraitRef

val RsTraitRef.resolveToTrait: RsTraitItem?
    get() = path.reference.resolve() as? RsTraitItem

