/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.settings

import com.intellij.openapi.options.advanced.AdvancedSettings

fun getMaximumRecursionLimit(): Int {
    val value = AdvancedSettings.getInt("org.rust.macros.maximum.recursion.limit")
    return if (value <= 0) Int.MAX_VALUE else value
}
