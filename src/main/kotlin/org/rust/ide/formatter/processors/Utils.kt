/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter.processors

import com.intellij.application.options.CodeStyle
import com.intellij.lang.ASTNode
import org.rust.ide.formatter.settings.RsCodeStyleSettings

fun shouldRunPunctuationProcessor(element: ASTNode): Boolean {
    val psi = element.psi
    if (!psi.isValid) return false // EA-110296, element might be invalid for some plugins
    return !CodeStyle.getCustomSettings(psi.containingFile, RsCodeStyleSettings::class.java).PRESERVE_PUNCTUATION
}
