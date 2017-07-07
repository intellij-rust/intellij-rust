/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.ext.RsDocAndAttributeOwner
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.queryAttributes
import org.rust.lang.core.psi.ext.superMods
import org.rust.lang.core.psi.ext.ancestors

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
        .filter { it.metaItemArgs?.metaItemList.orEmpty().any { it.text == id || it.text == BadStyle.id } }
        .mapNotNull { RsLintLevel.valueForId(it.identifier.text) }
        .firstOrNull()

    private fun superModsLevel(el: PsiElement)
        = el.ancestors
        .filterIsInstance<RsMod>()
        .lastOrNull()
        ?.superMods
        ?.mapNotNull { explicitLevel(it) }?.firstOrNull()
}
