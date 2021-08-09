/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.import

import com.intellij.util.TextWithIcon

class RsImportCandidateCellRenderer : RsImportCandidateCellRendererBase() {
    override fun getItemLocation(value: Any?): TextWithIcon? {
        val (text, icon) = textWithIcon(value) ?: return null
        return TextWithIcon(text, icon)
    }
}
