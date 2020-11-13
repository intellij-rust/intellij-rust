/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType

sealed class MacroExpansionAndParsingError {
    data class ExpansionError(val error: MacroExpansionError) : MacroExpansionAndParsingError()
    class ParsingError(val expansionText: CharSequence, val context: MacroExpansionContext) : MacroExpansionAndParsingError()
}

sealed class MacroExpansionError {
    data class Matching(val errors: List<MacroMatchingError>) : MacroExpansionError()
    object DefSyntax : MacroExpansionError()
}

sealed class MacroMatchingError(macroCallBody: PsiBuilder) {
    val offsetInCallBody: Int = macroCallBody.currentOffset

    class PatternSyntax(macroCallBody: PsiBuilder): MacroMatchingError(macroCallBody) {
        override fun toString(): String = "PatternSyntax"
    }
    class ExtraInput(macroCallBody: PsiBuilder): MacroMatchingError(macroCallBody) {
        override fun toString(): String = "ExtraInput"
    }
    class UnmatchedToken(macroCallBody: PsiBuilder, node: ASTNode): MacroMatchingError(macroCallBody) {
        val expectedToken: IElementType = node.elementType
        val expectedText: String = node.text
        val actualToken: IElementType? = macroCallBody.tokenType
        val actualText: String? = macroCallBody.tokenText

        override fun toString(): String = "UnmatchedToken($expectedToken(`$expectedText`) != $actualToken(`$actualText`))"
    }
    class FragmentNotParsed(macroCallBody: PsiBuilder, val kind: FragmentKind): MacroMatchingError(macroCallBody) {
        override fun toString(): String = "FragmentNotParsed($kind)"
    }
    class EmptyGroup(macroCallBody: PsiBuilder): MacroMatchingError(macroCallBody) {
        override fun toString(): String = "EmptyGroup"
    }
    class TooFewGroupElements(macroCallBody: PsiBuilder): MacroMatchingError(macroCallBody) {
        override fun toString(): String = "TooFewGroupElements"
    }
    class Nesting(macroCallBody: PsiBuilder, val variableName: String): MacroMatchingError(macroCallBody) {
        override fun toString(): String = "Nesting($variableName)"
    }
}
