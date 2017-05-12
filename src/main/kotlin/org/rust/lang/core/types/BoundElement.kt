package org.rust.lang.core.types

import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.types.ty.TypeArguments
import org.rust.lang.core.types.ty.emptyTypeArguments

/* Represents a potentially generic Psi Element, like
 * `fn make_t<T>() -> T { ... }`, together with actual
 * type arguments, like `T := i32`.
 */
data class BoundElement<out E : RsCompositeElement>(
    val element: E,
    val typeArguments: TypeArguments = emptyTypeArguments
)

