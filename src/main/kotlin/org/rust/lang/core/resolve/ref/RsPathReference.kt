/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.types.BoundElement

interface RsPathReference : RsReference {
    fun advancedResolve(): BoundElement<RsElement>?

    fun advancedMultiResolve(): List<BoundElement<RsElement>>
}
