/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import org.rust.ide.inspections.lints.RsLintLevel.*
import org.rust.lang.core.psi.ext.*

/**
 * Rust lints.
 */
enum class RsLint(
    val id: String,
    val groupIds: List<String> = emptyList(),
    val defaultLevel: RsLintLevel = WARN
) {
    NonSnakeCase("non_snake_case", listOf("bad_style", "nonstandard_style")),
    NonCamelCaseTypes("non_camel_case_types", listOf("bad_style", "nonstandard_style")),
    NonUpperCaseGlobals("non_upper_case_globals", listOf("bad_style", "nonstandard_style")),
    Deprecated("deprecated") {
        override fun toHighlightingType(level: RsLintLevel): ProblemHighlightType =
            when (level) {
                WARN -> ProblemHighlightType.LIKE_DEPRECATED
                else -> super.toHighlightingType(level)
            }
    },

    UnusedVariables("unused_variables", listOf("unused")) {
        override fun toHighlightingType(level: RsLintLevel): ProblemHighlightType =
            when (level) {
                WARN -> ProblemHighlightType.LIKE_UNUSED_SYMBOL
                else -> super.toHighlightingType(level)
            }
    },

    WhileTrue("while_true") {
        override fun toHighlightingType(level: RsLintLevel): ProblemHighlightType =
            when (level) {
                WARN -> ProblemHighlightType.WEAK_WARNING
                else -> super.toHighlightingType(level)
            }
    },

    NeedlessLifetimes("clippy::needless_lifetimes", listOf("clippy::complexity", "clippy::all", "clippy")) {
        override fun toHighlightingType(level: RsLintLevel): ProblemHighlightType =
            when (level) {
                WARN -> ProblemHighlightType.WEAK_WARNING
                else -> super.toHighlightingType(level)
            }
    };

    protected open fun toHighlightingType(level: RsLintLevel): ProblemHighlightType =
        when (level) {
            ALLOW -> ProblemHighlightType.INFORMATION
            WARN -> ProblemHighlightType.WARNING
            DENY, FORBID -> ProblemHighlightType.GENERIC_ERROR
        }

    fun getProblemHighlightType(element: PsiElement): ProblemHighlightType = toHighlightingType(levelFor(element))

    /**
     * Returns the level of the lint for the given PSI element.
     */
    fun levelFor(el: PsiElement): RsLintLevel = explicitLevel(el) ?: superModsLevel(el) ?: defaultLevel

    private fun explicitLevel(el: PsiElement): RsLintLevel? = el.ancestors
        .filterIsInstance<RsDocAndAttributeOwner>()
        .flatMap { it.queryAttributes.metaItems }
        .filter { it.metaItemArgs?.metaItemList.orEmpty().any { it.id == id || it.id in groupIds } }
        .mapNotNull { it.name?.let { RsLintLevel.valueForId(it) } }
        .firstOrNull()

    private fun superModsLevel(el: PsiElement): RsLintLevel? = el.ancestors
        .filterIsInstance<RsMod>()
        .lastOrNull()
        ?.superMods
        ?.mapNotNull { explicitLevel(it) }
        ?.firstOrNull()
}
