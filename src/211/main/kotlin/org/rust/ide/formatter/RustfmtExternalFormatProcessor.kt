/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

class RustfmtExternalFormatProcessor : RustfmtExternalFormatProcessorBase() {
    @Suppress("UnstableApiUsage")
    override fun format(
        source: PsiFile,
        range: TextRange,
        canChangeWhiteSpacesOnly: Boolean,
        keepLineBreaks: Boolean, // Always `false`?
        enableBulkUpdate: Boolean
    ): TextRange? {
        return formatWithRustfmtOrBuiltinFormatter(source, range, canChangeWhiteSpacesOnly)
    }
}
