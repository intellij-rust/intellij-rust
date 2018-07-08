/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.regions

object ReUnknown : Region() {
    override fun toString(): String = "'_"
}
