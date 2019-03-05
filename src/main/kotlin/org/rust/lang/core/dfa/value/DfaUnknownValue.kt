/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.dfa.value

object DfaUnknownValue : DfaValue(null) {
    override fun toString(): String = "{?}"

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = 0
}
