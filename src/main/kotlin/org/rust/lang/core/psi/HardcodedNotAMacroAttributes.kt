/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.openapiext.isUnitTestMode

private val RS_HARDCODED_NOT_A_MACRO_ATTRIBUTES: Map<String, Set<String>> = mapOf(
    "tokio_macros" to setOf("test", "main"),
)

fun isHardcodedNotAMacro(packageName: String, macroName: String): Boolean {
    if (RS_HARDCODED_NOT_A_MACRO_ATTRIBUTES[packageName]?.contains(macroName) == true) {
        return true
    }

    return isUnitTestMode && packageName == "test_proc_macros" && macroName == "attr_hardcoded_not_a_macro"
}
