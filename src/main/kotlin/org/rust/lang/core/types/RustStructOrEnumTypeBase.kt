package org.rust.lang.core.types

import org.rust.lang.core.psi.*

abstract class RustStructOrEnumTypeBase : RustTypeBase() {

    abstract val item: RustStructOrEnumItemElement

}
