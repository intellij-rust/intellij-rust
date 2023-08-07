/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.injected

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.util.text.CharArrayUtil
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.bodyTextRange
import org.rust.lang.core.psi.ext.macroBody
import org.rust.lang.core.psi.ext.startOffset

class RsMacroCallManipulator : AbstractElementManipulator<RsMacroCall>() {
    override fun handleContentChange(element: RsMacroCall, range: TextRange, newContent: String): RsMacroCall {
        val oldText = element.text
        val newText = "${oldText.substring(0, range.startOffset)}$newContent${oldText.substring(range.endOffset)}"

        val newMacroCall = RsPsiFactory(element.project).createFile("m!$newText").firstChild as? RsMacroCall
            ?: error(newText)
        return element.replace(newMacroCall) as RsMacroCall
    }

    /**
     * Used in "Inject language or reference" intention action to find a text range for injection.
     * See `InjectLanguageIntentionTest`.
     */
    override fun getRangeInElement(element: RsMacroCall): TextRange {
        val bodyTextRange = element.bodyTextRange?.shiftLeft(element.startOffset) ?: return super.getRangeInElement(element)
        val macroBody = element.macroBody ?: return bodyTextRange

        var trimmedStart = bodyTextRange.startOffset
        var trimmedEnd = bodyTextRange.endOffset

        // Trim start
        val firstNonSpaceIndex = CharArrayUtil.shiftForward(macroBody, 0, " \t")
        if (firstNonSpaceIndex < macroBody.length && macroBody[firstNonSpaceIndex] == '\n') {
            trimmedStart = bodyTextRange.startOffset + firstNonSpaceIndex + 1
        }

        // Trim end
        val lastNonSpaceIndex = CharArrayUtil.shiftBackward(macroBody, firstNonSpaceIndex, macroBody.lastIndex, " \t")
        if (lastNonSpaceIndex > firstNonSpaceIndex && macroBody[lastNonSpaceIndex] == '\n') {
            trimmedEnd = bodyTextRange.endOffset - (macroBody.length - lastNonSpaceIndex) + 1
        }

        return if (trimmedStart < trimmedEnd) {
            TextRange(trimmedStart, trimmedEnd)
        } else {
            bodyTextRange
        }
    }
}
