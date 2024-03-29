/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.psi.PsiElement
import org.rust.ide.refactoring.isValidRustVariableIdentifier
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.ext.elementType

const val RS_RAW_PREFIX = "r#"
private val CAN_NOT_BE_ESCAPED = listOf("self", "super", "crate", "Self")

val String.canBeEscaped: Boolean
    get() = this !in CAN_NOT_BE_ESCAPED && !CAN_NOT_BE_ESCAPED.any { this.startsWith("$it::") }

fun String.unescapeIdentifier(): String = removePrefix(RS_RAW_PREFIX)
fun String.escapeIdentifierIfNeeded(): String =
    if (isValidRustVariableIdentifier(this) || !this.canBeEscaped) this else "$RS_RAW_PREFIX$this"

val PsiElement.unescapedText: String get() {
    val text = text ?: return ""
    return if (elementType == IDENTIFIER) text.unescapeIdentifier() else text
}
