/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter.processors

import com.intellij.lang.ASTNode
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import org.rust.ide.formatter.rust


fun shouldRunPunctuationProcessor(element: ASTNode): Boolean {
    val psi = element.psi
    if (!psi.isValid) return false // EA-110296, element might be invalid for some plugins
    return !CodeStyleSettingsManager.getInstance(psi.project).currentSettings.rust.PRESERVE_PUNCTUATION
}
