/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsMatchArm
import org.rust.lang.core.psi.RsOrPat
import org.rust.lang.core.psi.RsPat

@Deprecated("Support `RsOrPat`")
val RsMatchArm.patList: List<RsPat>
    get() {
        val pat = pat
        return if (pat is RsOrPat) pat.patList else listOf(pat)
    }
