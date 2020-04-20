/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.ext.*

/**
 * Rust lints.
 */
enum class RsLint(
    val id: String,
    val groupIds: List<String> = emptyList(),
    val defaultLevel: RsLintLevel = RsLintLevel.WARN
) {
    NonSnakeCase("non_snake_case", listOf("bad_style")),
    NonCamelCaseTypes("non_camel_case_types", listOf("bad_style")),
    NonUpperCaseGlobals("non_upper_case_globals", listOf("bad_style")),
    Deprecated("deprecated"),
    UnusedVariables("unused_variables", listOf("unused")),
    NeedlessLifetimes("clippy::needless_lifetimes", listOf("clippy::complexity", "clippy::all", "clippy"));

    /**
     * Returns the level of the lint for the given PSI element.
     */
    fun levelFor(el: PsiElement) = explicitLevel(el) ?: superModsLevel(el) ?: defaultLevel

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
