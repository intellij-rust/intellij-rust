/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.codeInspection.ProblemHighlightType

class RsLintHighlightingType private constructor(
    val allow: ProblemHighlightType = ProblemHighlightType.INFORMATION,
    val warn: ProblemHighlightType = ProblemHighlightType.WARNING,
    val deny: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR,
    val forbid: ProblemHighlightType = deny
) {
    companion object {
        val DEFAULT = RsLintHighlightingType()
        val WEAK_WARNING = RsLintHighlightingType(warn = ProblemHighlightType.WEAK_WARNING)
        val UNUSED_SYMBOL = RsLintHighlightingType(warn = ProblemHighlightType.LIKE_UNUSED_SYMBOL)
        val DEPRECATED = RsLintHighlightingType(warn = ProblemHighlightType.LIKE_DEPRECATED)
    }
}
