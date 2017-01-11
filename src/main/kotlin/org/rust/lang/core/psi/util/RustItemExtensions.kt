package org.rust.lang.core.psi.util

import org.rust.lang.core.psi.RustGenericDeclaration
import org.rust.lang.core.psi.RustTraitItemElement
import org.rust.lang.core.psi.RustTraitRefElement
import org.rust.lang.core.psi.RustTypeParameterElement

/**
 *  `RustItemElement` related extensions
 */

val RustGenericDeclaration.typeParams: List<RustTypeParameterElement>
    get() = typeParameterList?.typeParameterList.orEmpty()

val RustTraitRefElement.trait: RustTraitItemElement?
    get() = path.reference.resolve() as? RustTraitItemElement
