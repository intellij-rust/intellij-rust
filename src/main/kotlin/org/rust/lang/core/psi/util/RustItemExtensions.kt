package org.rust.lang.core.psi.util

import org.rust.lang.core.psi.*
import org.rust.lang.core.symbols.RustQualifiedPath
import org.rust.lang.core.types.unresolved.RustUnresolvedPathType
import org.rust.lang.core.types.util.type

/**
 *  `RustItemElement` related extensions
 */

val RustGenericDeclaration.typeParams: List<RustTypeParamElement>
    get() = genericParams?.typeParamList.orEmpty()

val RustTraitRefElement.trait: RustTraitItemElement?
    get() = path.reference.resolve() as? RustTraitItemElement

val RustUseItemElement.aliased: String?
    get() = stub?.alias ?: alias?.name
