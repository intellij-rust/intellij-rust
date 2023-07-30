/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.psi.PsiElement
import org.rust.ide.inspections.lints.RsLintLevel.DENY
import org.rust.ide.inspections.lints.RsLintLevel.WARN
import org.rust.lang.core.psi.ext.*

/**
 * Rust lints.
 */
sealed class RsLint(
    val id: String,
    private val groupIds: List<String> = emptyList(),
    private val defaultLevel: RsLintLevel = WARN
) {
    // Rustc lints
    // warnings
    object NonSnakeCase : RsLint("non_snake_case", listOf("bad_style", "nonstandard_style"))
    object NonCamelCaseTypes : RsLint("non_camel_case_types", listOf("bad_style", "nonstandard_style"))
    object NonUpperCaseGlobals : RsLint("non_upper_case_globals", listOf("bad_style", "nonstandard_style"))
    object Deprecated : RsLint("deprecated")
    object UnusedVariables : RsLint("unused_variables", listOf("unused"))
    object UnusedImports : RsLint("unused_imports", listOf("unused"))
    object UnusedMut : RsLint("unused_mut", listOf("unused"))
    object UnreachablePattern : RsLint("unreachable_patterns", listOf("unused"))
    object WhileTrue : RsLint("while_true")
    object UnreachableCode : RsLint("unreachable_code")
    object BareTraitObjects : RsLint("bare_trait_objects", listOf("rust_2018_idioms"))
    object NonShorthandFieldPatterns : RsLint("non_shorthand_field_patterns")
    object UnusedQualifications : RsLint("unused_qualifications")
    object UnusedMustUse : RsLint("unused_must_use", listOf("unused"))
    object RedundantSemicolons : RsLint("redundant_semicolons", listOf("unused"))
    object UnusedLabels : RsLint("unused_labels", listOf("unused"))
    object PathStatements : RsLint("path_statements", listOf("unused"))
    // errors
    object UnknownCrateTypes : RsLint("unknown_crate_types", defaultLevel = DENY)
    // CLippy lints
    object NeedlessLifetimes : RsLint("clippy::needless_lifetimes", listOf("clippy::complexity", "clippy::all", "clippy"))
    object DoubleMustUse : RsLint("clippy::double_must_use", listOf("clippy::style", "clippy::all", "clippy"))
    object WrongSelfConvention : RsLint("clippy::wrong_self_convention", listOf("clippy::style", "clippy::all", "clippy"))
    object UnnecessaryCast : RsLint("clippy::unnecessary_cast", listOf("clippy::complexity", "clippy::all", "clippy"))

    // External linter lint
    class ExternalLinterLint(id: String) : RsLint(id) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ExternalLinterLint
            if (id != other.id) return false
            return true
        }

        override fun hashCode(): Int = id.hashCode()
    }

    /**
     * Returns the level of the lint for the given PSI element.
     */
    fun levelFor(el: PsiElement): RsLintLevel = explicitLevel(el) ?: superModsLevel(el) ?: defaultLevel

    fun explicitLevel(el: PsiElement): RsLintLevel? = el.contexts
        .filterIsInstance<RsDocAndAttributeOwner>()
        .flatMap { it.queryAttributes.metaItems.toList().asReversed().asSequence() }
        .filter { it.metaItemArgs?.metaItemList.orEmpty().any { item -> item.id == id || item.id in groupIds } }
        .firstNotNullOfOrNull { it.name?.let { name -> RsLintLevel.valueForId(name) } }

    private fun superModsLevel(el: PsiElement): RsLintLevel? = el.contexts
        .filterIsInstance<RsMod>()
        .lastOrNull()
        ?.superMods
        ?.firstNotNullOfOrNull { explicitLevel(it) }
}
