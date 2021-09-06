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
enum class RsLint(
    val id: String,
    private val groupIds: List<String> = emptyList(),
    private val defaultLevel: RsLintLevel = WARN
) {
    NonSnakeCase("non_snake_case", listOf("bad_style", "nonstandard_style")),
    NonCamelCaseTypes("non_camel_case_types", listOf("bad_style", "nonstandard_style")),
    NonUpperCaseGlobals("non_upper_case_globals", listOf("bad_style", "nonstandard_style")),
    Deprecated("deprecated"),
    UnusedVariables("unused_variables", listOf("unused")),
    UnusedImports("unused_imports", listOf("unused")),
    WhileTrue("while_true"),
    NeedlessLifetimes("clippy::needless_lifetimes", listOf("clippy::complexity", "clippy::all", "clippy")),
    UnreachablePattern("unreachable_patterns", listOf("unused")),
    UnreachableCode("unreachable_code"),
    BareTraitObjects("bare_trait_objects", listOf("rust_2018_idioms")),
    NonShorthandFieldPatterns("non_shorthand_field_patterns"),
    UnknownCrateTypes("unknown_crate_types", defaultLevel = DENY);

    /**
     * Returns the level of the lint for the given PSI element.
     */
    fun levelFor(el: PsiElement): RsLintLevel = explicitLevel(el) ?: superModsLevel(el) ?: defaultLevel

    private fun explicitLevel(el: PsiElement): RsLintLevel? = el.ancestors
        .filterIsInstance<RsDocAndAttributeOwner>()
        .flatMap { it.queryAttributes.metaItems.toList().asReversed().asSequence() }
        .filter { it.metaItemArgs?.metaItemList.orEmpty().any { item -> item.id == id || item.id in groupIds } }
        .mapNotNull { it.name?.let { name -> RsLintLevel.valueForId(name) } }
        .firstOrNull()

    private fun superModsLevel(el: PsiElement): RsLintLevel? = el.ancestors
        .filterIsInstance<RsMod>()
        .lastOrNull()
        ?.superMods
        ?.mapNotNull { explicitLevel(it) }
        ?.firstOrNull()
}
