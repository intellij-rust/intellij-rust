/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.settings

import com.intellij.openapi.options.advanced.AdvancedSettings

const val MACROS_MAXIMUM_RECURSION_LIMIT_SETTING_KEY: String = "org.rust.macros.maximum.recursion.limit"

fun getMaximumRecursionLimit(): Int {
    val value = AdvancedSettings.getInt(MACROS_MAXIMUM_RECURSION_LIMIT_SETTING_KEY)
    return if (value <= 0) Int.MAX_VALUE else value
}
