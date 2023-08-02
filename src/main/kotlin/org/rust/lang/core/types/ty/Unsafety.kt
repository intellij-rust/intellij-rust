/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

enum class Unsafety {
    Unsafe,
    Normal;

    companion object {
        fun fromBoolean(unsafe: Boolean): Unsafety {
            return if (unsafe) Unsafe else Normal
        }
    }
}
