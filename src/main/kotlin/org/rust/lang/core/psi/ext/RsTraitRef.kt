/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTraitRef
import org.rust.lang.core.types.BoundElement

fun RsTraitRef.resolveToTrait(): RsTraitItem? =
    path.reference?.resolve() as? RsTraitItem

fun RsTraitRef.resolveToBoundTrait(): BoundElement<RsTraitItem>? =
    path.reference?.advancedResolve()?.downcast()

