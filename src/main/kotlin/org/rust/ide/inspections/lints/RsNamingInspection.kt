/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.google.common.annotations.VisibleForTesting
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.inspections.RsWithMacrosInspectionVisitor
import org.rust.ide.inspections.fixes.RenameFix
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

/**
 * Base class for naming inspections. Implements the core logic of checking names
 * and registering problems.
 */
abstract class RsNamingInspection(
    private val elementType: String,
    private val styleName: String,
    private val elementTitle: String = elementType
) : RsLintInspection() {
    override fun getDisplayName(): String = "$elementTitle naming convention"

    override val isSyntaxOnly: Boolean = true

    fun inspect(id: PsiElement?, holder: RsProblemsHolder, fix: Boolean = true) {
        if (id == null) return
        val name = id.unescapedText
        val suggestedName = checkName(name) ?: return

        val fixEl = id.parent
        val fixes = if (fix && fixEl is PsiNamedElement) listOf(RenameFix(fixEl, suggestedName)) else emptyList()

        holder.registerLintProblem(
            id,
            "$elementType `$name` should have $styleName case name such as `$suggestedName`",
            fixes = fixes
        )
    }

    /**
     * Suggests how to rename given [name] according to the corresponding naming convention.
     * Returns `null` if the [name] matches the naming convention.
     */
    @VisibleForTesting
    abstract fun checkName(name: String): String?
}

/**
 * Checks if the name is CamelCase.
 */
open class RsCamelCaseNamingInspection(
    elementType: String,
    elementTitle: String = elementType
) : RsNamingInspection(elementType, "a camel", elementTitle) {

    override fun getLint(element: PsiElement): RsLint = RsLint.NonCamelCaseTypes

    override fun checkName(name: String): String? {
        val str = name.trim('_')
        return if (str.isCamelCase()) {
            null
        } else {
            if (str.isEmpty()) "CamelCase" else suggestName(name)
        }
    }

    private fun suggestName(name: String): String {
        val result = name.toCamelCase()
        return result.ifEmpty { "CamelCase" }
    }
}

private val Char.canStartWord: Boolean get() = isUpperCase() || isDigit()

fun String.toCamelCase(): String {
    val result = StringBuilder(length)
    var wasUnderscore = true
    var startWord = true
    for (char in this) {
        when {
            char == '_' -> wasUnderscore = true
            wasUnderscore || startWord && char.canStartWord -> {
                result.append(char.uppercase())
                wasUnderscore = false
                startWord = false
            }
            else -> {
                startWord = char.isLowerCase()
                result.append(char.lowercase())
            }
        }
    }
    return result.toString()
}

val Char.hasCase: Boolean get() = isLowerCase() || isUpperCase()

fun String.isCamelCase(): Boolean =
    isNotEmpty()
        && !first().isLowerCase()
        && !contains("__")
        && !zipWithNext().any { (fst, snd) -> (fst.hasCase && snd == '_') || (snd.hasCase && fst == '_') }

/**
 * Checks if the name is snake_case.
 */
open class RsSnakeCaseNamingInspection(elementType: String) : RsNamingInspection(elementType, "a snake") {

    override fun getLint(element: PsiElement): RsLint = RsLint.NonSnakeCase

    override fun checkName(name: String): String? {
        val str = name.trim('_')
        // Some characters don't have case so we can't use `isLowerCase` here
        return if (str.isNotEmpty() && str.all { !it.isUpperCase() }) {
            null
        } else {
            if (str.isEmpty()) "snake_case" else name.toSnakeCase(false)
        }
    }
}

/**
 * Checks if the name is UPPER_CASE.
 */
open class RsUpperCaseNamingInspection(elementType: String) : RsNamingInspection(elementType, "an upper") {

    override fun getLint(element: PsiElement): RsLint = RsLint.NonUpperCaseGlobals

    override fun checkName(name: String): String? {
        val str = name.trim('_')
        return if (str.isNotEmpty() && str.none { it.isLowerCase() }) {
            null
        } else {
            if (str.isEmpty()) "UPPER_CASE" else name.toSnakeCase(true)
        }
    }
}

fun String.toSnakeCase(upper: Boolean): String {
    val result = StringBuilder(length + 3)     // 3 is a reasonable margin for growth when `_`s are added
    result.append(takeWhile { it == '_' || it == '\'' })    // Preserve prefix
    var firstPart = true
    drop(result.length).splitToSequence('_').forEach pit@{ part ->
        if (part.isEmpty()) return@pit
        if (!firstPart) {
            result.append('_')
        }
        firstPart = false
        var newWord = false
        var firstWord = true
        part.forEach { char ->
            newWord = if (newWord && char.isUpperCase()) {
                if (!firstWord) {
                    result.append('_')
                }
                false
            } else {
                char.isLowerCase()
            }
            result.append(if (upper) char.uppercaseChar() else char.lowercaseChar())
            firstWord = false
        }
    }
    return result.toString()
}

//
// Concrete inspections
//

class RsArgumentNamingInspection : RsSnakeCaseNamingInspection("Argument") {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitPatBinding(el: RsPatBinding) {
                if (el.parent?.parent is RsValueParameter) {
                    inspect(el.identifier, holder)
                }
            }
        }
}

class RsConstNamingInspection : RsUpperCaseNamingInspection("Constant") {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitConstant2(o: RsConstant) {
                if (o.kind == RsConstantKind.CONST) {
                    inspect(o.identifier, holder)
                }
            }
        }
}

class RsStaticConstNamingInspection : RsUpperCaseNamingInspection("Static constant") {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitConstant2(o: RsConstant) {
                if (o.kind != RsConstantKind.CONST && o.owner is RsAbstractableOwner.Free) {
                    inspect(o.identifier, holder)
                }
            }
        }
}

class RsEnumNamingInspection : RsCamelCaseNamingInspection("Type", "Enum") {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitEnumItem2(o: RsEnumItem) = inspect(o.identifier, holder)
        }
}

class RsEnumVariantNamingInspection : RsCamelCaseNamingInspection("Enum variant") {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitEnumVariant(el: RsEnumVariant) = inspect(el.identifier, holder)
        }
}

class RsFunctionNamingInspection : RsSnakeCaseNamingInspection("Function") {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitFunction2(o: RsFunction) {
                if (o.owner is RsAbstractableOwner.Free) {
                    inspect(o.identifier, holder)
                }
            }
        }

    override fun isSuppressedFor(element: PsiElement): Boolean {
        if (super.isSuppressedFor(element)) return true

        val function = element.parent as? RsFunction ?: return false
        return function.isActuallyExtern && function.findOuterAttr("no_mangle") != null
    }
}

class RsMethodNamingInspection : RsSnakeCaseNamingInspection("Method") {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitFunction2(o: RsFunction) = when (o.owner) {
                is RsAbstractableOwner.Trait, is RsAbstractableOwner.Impl -> inspect(o.identifier, holder)
                else -> Unit
            }
        }
}

class RsLifetimeNamingInspection : RsSnakeCaseNamingInspection("Lifetime") {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitLifetimeParameter(el: RsLifetimeParameter) = inspect(el.quoteIdentifier, holder)
        }
}

class RsMacroNamingInspection : RsSnakeCaseNamingInspection("Macro") {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitMacro2(o: RsMacro) = inspect(o.nameIdentifier, holder)
        }
}

class RsModuleNamingInspection : RsSnakeCaseNamingInspection("Module") {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitModDeclItem(el: RsModDeclItem) = inspect(el.identifier, holder)
            override fun visitModItem2(o: RsModItem) = inspect(o.identifier, holder)
        }
}

class RsStructNamingInspection : RsCamelCaseNamingInspection("Type", "Struct") {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitStructItem2(o: RsStructItem) = inspect(o.identifier, holder)
        }
}

class RsFieldNamingInspection : RsSnakeCaseNamingInspection("Field") {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitNamedFieldDecl(o: RsNamedFieldDecl) = inspect(o.identifier, holder)
        }
}

class RsTraitNamingInspection : RsCamelCaseNamingInspection("Trait") {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitTraitItem2(o: RsTraitItem) = inspect(o.identifier, holder)
        }
}

class RsTypeAliasNamingInspection : RsCamelCaseNamingInspection("Type", "Type alias") {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitTypeAlias2(o: RsTypeAlias) {
                if (o.owner is RsAbstractableOwner.Free) {
                    inspect(o.identifier, holder)
                }
            }
        }
}

class RsAssocTypeNamingInspection : RsCamelCaseNamingInspection("Type", "Associated type") {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitTypeAlias2(o: RsTypeAlias) {
                if (o.owner is RsAbstractableOwner.Trait) {
                    inspect(o.identifier, holder)
                }
            }
        }
}


class RsTypeParameterNamingInspection : RsCamelCaseNamingInspection("Type parameter") {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitTypeParameter(el: RsTypeParameter) = inspect(el.identifier, holder)
        }
}

class RsVariableNamingInspection : RsSnakeCaseNamingInspection("Variable") {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitPatBinding(el: RsPatBinding) {
                if (el.isReferenceToConstant) return

                val pattern = PsiTreeUtil.getTopmostParentOfType(el, RsPat::class.java) ?: return
                when (pattern.parent) {
                    is RsLetDecl -> inspect(el.identifier, holder)
                }
            }
        }
}
