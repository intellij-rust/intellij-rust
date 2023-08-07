/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions.util.macros

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.psi.PsiFile
import org.rust.lang.core.macros.RangeMap

class RsIntentionInsideMacroExpansionContext(
    val originalFile: PsiFile,
    val documentCopy: Document,
    val rangeMap: RangeMap,
    val rootMacroBodyRange: RangeMarker,
    val changedRanges: MutableList<RangeMarker> = mutableListOf(),
    var finished: Boolean = false,
    var broken: Boolean = false,
    var applyChangesToOriginalDoc: Boolean = true,
) {
    val rootMacroCallBodyOffset: Int get() = rootMacroBodyRange.startOffset
}
