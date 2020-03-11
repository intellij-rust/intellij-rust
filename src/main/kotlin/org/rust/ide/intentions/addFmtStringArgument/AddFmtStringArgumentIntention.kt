/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions.addFmtStringArgument

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapiext.isUnitTestMode
import com.intellij.psi.PsiElement
import org.rust.ide.intentions.RsElementBaseIntentionAction
import org.rust.lang.core.macros.expansionContext
import org.rust.lang.core.macros.isExprOrStmtContext
import org.rust.lang.core.psi.RsElementTypes.STRING_LITERAL
import org.rust.lang.core.psi.RsExpressionCodeFragment
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.runWriteCommandAction

class AddFmtStringArgumentIntention : RsElementBaseIntentionAction<AddFmtStringArgumentIntention.Context>() {
    override fun getText(): String = "Add format string argument"
    override fun getFamilyName(): String = text

    class Context(val literal: RsLitExpr, val macroCall: RsMacroCall)

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        if (element.elementType != STRING_LITERAL) return null
        val literal = element.ancestorOrSelf<RsLitExpr>() ?: return null

        // Caret must be inside a literal, not right before or right after it
        if (!literal.containsOffset(editor.caretModel.offset)) return null

        val macroCall = literal.ancestorOrSelf<RsMacroCall>() ?: return null
        if (!macroCall.isExprOrStmtContext || macroCall.macroName !in FORMAT_MACROS) return null

        return Context(literal, macroCall)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val macroCallExpr = ctx.macroCall.parent as? RsElement ?: return

        val literal = ctx.literal
        if (!literal.containsOffset(editor.caretModel.offset)) return

        val caretOffsetInLiteral = editor.caretModel.offset - literal.textOffset - 1
        if (caretOffsetInLiteral < 0) return

        val oldString = literal.text.trim('\"')
        val oldStringUntilCaret = oldString.substring(0, caretOffsetInLiteral)
        val placeholderRegex = """\{(:[a-zA-Z0-9.,?]*)?}""".toRegex()

        val result = placeholderRegex.findAll(oldStringUntilCaret)
        val placeholderNumber = result.count()

        val codeFragment = RsExpressionCodeFragment(project, CODE_FRAGMENT_TEXT, macroCallExpr)

        if (isUnitTestMode) {
            addFmtStringArgument(project, editor, ctx, codeFragment, caretOffsetInLiteral, placeholderNumber)
        } else {
            RsAddFmtStringArgumentPopup.show(editor, project, codeFragment) {
                addFmtStringArgument(project, editor, ctx, codeFragment, caretOffsetInLiteral, placeholderNumber)
            }
        }
    }

    private fun addFmtStringArgument(
        project: Project,
        editor: Editor,
        ctx: Context,
        codeFragment: RsExpressionCodeFragment,
        caretOffsetInLiteral: Int,
        placeholderNumber: Int
    ) {
        val psiFactory = RsPsiFactory(project)

        val macroCall = ctx.macroCall
        val argument = macroCall.formatMacroArgument ?: return
        val arguments = argument.formatMacroArgList

        val newPlaceholder = "{}"
        val oldString = ctx.literal.text.trim('\"')
        val prefix = oldString.substring(0, caretOffsetInLiteral)
        val suffix = oldString.substring(caretOffsetInLiteral)
        val newString = "\"$prefix$newPlaceholder$suffix\""
        val newArgument = codeFragment.expr?.text ?: return

        val newArgs = if (arguments.size == 1) {
            // e.g. `println!("x = <caret>")`
            listOf(newString, newArgument)
        } else {
            // e.g. `println!("x = {}, y = <caret>", x)` or `write!(f, "x = {}, y = <caret>", x)`
            val literalPosition = arguments.indexOfFirst { it.expr == ctx.literal }.takeIf { it >= 0 } ?: return
            val argsBeforeLiteral = arguments.take(literalPosition).map { it.text }
            val argsAfterLiteral = arguments.drop(literalPosition + 1).map { it.text }
            val newArgsAfterLiteral =
                argsAfterLiteral.take(placeholderNumber) + newArgument + argsAfterLiteral.drop(placeholderNumber)

            argsBeforeLiteral + newString + newArgsAfterLiteral
        }
        val newMacroCall = psiFactory.createMacroCall(
            macroCall.expansionContext,
            macroCall.bracesKind ?: return,
            macroCall.macroName,
            *newArgs.toTypedArray()
        )

        project.runWriteCommandAction {
            macroCall.replace(newMacroCall) as RsMacroCall
            editor.caretModel.moveToOffset(editor.caretModel.offset + newPlaceholder.length)
        }
    }

    companion object {
        private val FORMAT_MACROS: Set<String> =
            hashSetOf("format", "write", "writeln", "print", "println", "eprint", "eprintln", "format_args")

        @JvmField
        @VisibleForTesting
        var CODE_FRAGMENT_TEXT: String = ""
    }
}
