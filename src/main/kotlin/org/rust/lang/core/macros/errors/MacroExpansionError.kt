/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.errors

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.macros.decl.FragmentKind
import java.io.PrintWriter
import java.io.StringWriter

sealed class MacroExpansionError

sealed class DeclMacroExpansionError : MacroExpansionError() {
    data class Matching(val errors: List<MacroMatchingError>) : DeclMacroExpansionError()
    object DefSyntax : DeclMacroExpansionError()
}

sealed class MacroMatchingError(macroCallBody: PsiBuilder) {
    val offsetInCallBody: Int = macroCallBody.currentOffset

    class PatternSyntax(macroCallBody: PsiBuilder) : MacroMatchingError(macroCallBody) {
        override fun toString(): String = "PatternSyntax"
    }

    class ExtraInput(macroCallBody: PsiBuilder) : MacroMatchingError(macroCallBody) {
        override fun toString(): String = "ExtraInput"
    }

    class UnmatchedToken(macroCallBody: PsiBuilder, node: ASTNode) : MacroMatchingError(macroCallBody) {
        private val expectedToken: IElementType = node.elementType
        private val expectedText: String = node.text
        private val actualToken: IElementType? = macroCallBody.tokenType
        private val actualText: String? = macroCallBody.tokenText

        override fun toString(): String = "UnmatchedToken($expectedToken(`$expectedText`) != $actualToken(`$actualText`))"
    }

    class FragmentNotParsed(macroCallBody: PsiBuilder, val kind: FragmentKind) : MacroMatchingError(macroCallBody) {
        override fun toString(): String = "FragmentNotParsed($kind)"
    }

    class EmptyGroup(macroCallBody: PsiBuilder) : MacroMatchingError(macroCallBody) {
        override fun toString(): String = "EmptyGroup"
    }

    class TooFewGroupElements(macroCallBody: PsiBuilder) : MacroMatchingError(macroCallBody) {
        override fun toString(): String = "TooFewGroupElements"
    }

    class Nesting(macroCallBody: PsiBuilder, private val variableName: String) : MacroMatchingError(macroCallBody) {
        override fun toString(): String = "Nesting($variableName)"
    }
}

sealed class ProcMacroExpansionError : MacroExpansionError() {

    /** An error occurred on the proc macro expander side. This usually means a panic from a proc-macro */
    data class ServerSideError(val message: String) : ProcMacroExpansionError() {
        override fun toString(): String = "${super.toString()}(message = \"$message\")"
    }

    /**
     * An exception thrown during communicating with the proc macro expander.
     * This can means that the process was killed by a user, for example.
     */
    data class ExceptionThrown(val cause: Exception) : ProcMacroExpansionError() {
        override fun toString(): String = StringWriter().also { writer ->
            writer.write("${super.toString()}(\n")
            cause.printStackTrace(PrintWriter(writer))
            writer.write("\n)")
        }.toString()
    }

    object Timeout : ProcMacroExpansionError()
    object CantRunExpander : ProcMacroExpansionError()
    object ExecutableNotFound : ProcMacroExpansionError()
    object MacroCallSyntax : ProcMacroExpansionError()

    override fun toString(): String = "${ProcMacroExpansionError::class.simpleName}.${javaClass.simpleName}"
}
