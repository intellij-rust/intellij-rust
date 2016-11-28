package org.rust.ide.inspections

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustDocAndAttributeOwner
import org.rust.lang.core.psi.RustMod
import org.rust.lang.core.psi.queryAttributes
import org.rust.lang.core.psi.superMods
import org.rust.lang.core.psi.util.ancestors

/**
 * Rust lints.
 */
enum class RustLint(
    val id: String,
    val defaultLevel: RustLintLevel = RustLintLevel.WARN
) {
    NonSnakeCase("non_snake_case"),
    NonCamelCaseTypes("non_camel_case_types"),
    NonUpperCaseGlobals("non_upper_case_globals");

    /**
     * Returns the level of the lint for the given PSI element.
     */
    fun levelFor(el: PsiElement)
        = explicitLevel(el) ?: superModsLevel(el) ?: defaultLevel

    private fun explicitLevel(el: PsiElement)
        = el.ancestors
            .filterIsInstance<RustDocAndAttributeOwner>()
            .flatMap { it.queryAttributes.metaItems }
            .filter { it.metaItemList.any { it.text == id } }
            .mapNotNull { RustLintLevel.valueForId(it.identifier.text) }
            .firstOrNull()

    private fun superModsLevel(el: PsiElement)
        = el.ancestors
            .filterIsInstance<RustMod>()
            .lastOrNull()
            ?.superMods
            ?.mapNotNull { explicitLevel(it) }?.firstOrNull()
}
