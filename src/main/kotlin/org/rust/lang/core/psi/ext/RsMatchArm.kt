/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsMatchArm
import org.rust.lang.core.psi.RsPat

val RsMatchArm.patList: List<RsPat> get() = orPats.patList
