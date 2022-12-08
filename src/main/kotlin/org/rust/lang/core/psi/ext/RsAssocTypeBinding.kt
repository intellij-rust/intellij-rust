/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsAssocTypeBinding
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.types.BoundElement

// Current grammar allows to write assoc type bindings in method calls, e.g.
// `a.foo::<Item = i32>()`, so it's nullable
val RsAssocTypeBinding.parentPath: RsPath?
    get() = ancestorStrict()

fun RsAssocTypeBinding.resolveToBoundAssocType(): BoundElement<RsTypeAlias>? =
    path.reference?.advancedResolve()?.downcast()
