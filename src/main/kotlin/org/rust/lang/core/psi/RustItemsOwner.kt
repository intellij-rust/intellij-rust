package org.rust.lang.core.psi

import org.rust.lang.core.resolve.scope.RustResolveScope

interface RustItemsOwner : RustResolveScope {
    val itemList: List<RustItem>
}
