/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.types.BoundElement

interface RsPathReference : RsReference {
    fun advancedResolve(): BoundElement<RsCompositeElement>?

    fun advancedMultiResolve(): List<BoundElement<RsCompositeElement>>
}
