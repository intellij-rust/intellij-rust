package org.rust.lang.core.psi.util

import org.rust.lang.core.psi.RustGenericDeclaration
import org.rust.lang.core.psi.RustTraitItemElement
import org.rust.lang.core.psi.RustTraitRefElement
import org.rust.lang.core.psi.RustTypeParamElement

/**
 *  `RustItemElement` related extensions
 */

val RustGenericDeclaration.typeParams: List<RustTypeParamElement>
    get() = genericParams?.typeParamList.orEmpty()

val RustTraitRefElement.trait: RustTraitItemElement?
    get() = path.reference.resolve() as? RustTraitItemElement
