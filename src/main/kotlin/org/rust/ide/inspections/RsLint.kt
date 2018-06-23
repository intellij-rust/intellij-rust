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
    val defaultLevel: RsLintLevel = RsLintLevel.WARN
) {
    NonSnakeCase("non_snake_case"),
    NonCamelCaseTypes("non_camel_case_types"),
    NonUpperCaseGlobals("non_upper_case_globals"),
    BadStyle("bad_style");

    /**
     * Returns the level of the lint for the given PSI element.
     */
    fun levelFor(el: PsiElement)
        = explicitLevel(el) ?: superModsLevel(el) ?: defaultLevel

    private fun explicitLevel(el: PsiElement)
        = el.ancestors
        .filterIsInstance<RsDocAndAttributeOwner>()
        .flatMap { it.queryAttributes.metaItems }
        .filter { it.metaItemArgs?.metaItemList.orEmpty().any { it.name == id || it.name == BadStyle.id } }
        .mapNotNull { it.name?.let { RsLintLevel.valueForId(it) } }
        .firstOrNull()

    private fun superModsLevel(el: PsiElement)
        = el.ancestors
        .filterIsInstance<RsMod>()
        .lastOrNull()
        ?.superMods
        ?.mapNotNull { explicitLevel(it) }?.firstOrNull()
}
