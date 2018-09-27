/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsMacroExpr
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.stringLiteralValue
import org.rust.lang.core.resolve.DEFAULT_RECURSION_LIMIT
import java.nio.ByteBuffer

fun expandInclude(call: RsMacroCall): List<RsExpandedElement>? {
    val psiFactory = RsPsiFactory(call.project)
    val args = call.includeMacroArgument?.expr ?: return null
    val path = expandStringArgument(args) ?: return null
    val file = call.containingFile.virtualFile.parent.findFileByRelativePath(path) ?: return null
    val content = file.charset.decode(ByteBuffer.wrap(file.contentsToByteArray()))
    return psiFactory.parseExpandedTextWithContext(call, content)
}

private tailrec fun expandStringArgument(args: RsExpandedElement, depth: Int = 0): String? {
    if (depth > DEFAULT_RECURSION_LIMIT) return null
    return when (args) {
        is RsLitExpr -> args.stringLiteralValue
        is RsMacroExpr -> {
            val expansion = org.rust.lang.core.macros.expandMacro(args.macroCall).value.orEmpty()
            if (expansion.size != 1) {
                null
            } else {
                expandStringArgument(expansion.first(), depth + 1)
            }
        }
        else -> null
    }
}
