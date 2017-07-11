/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTraitRef
import org.rust.lang.core.types.BoundElement

val RsTraitRef.resolveToTrait: RsTraitItem?
    get() = path.reference.resolve() as? RsTraitItem

val RsTraitRef.resolveToBoundTrait: BoundElement<RsTraitItem>?
    get() = path.reference.advancedResolve()?.downcast<RsTraitItem>()

