/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.ide.inspections.fixes.RenameFix
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

/**
 * Base class for naming inspections. Implements the core logic of checking names
 * and registering problems.
 */
abstract class RsNamingInspection(
    val elementType: String,
    val styleName: String,
    val lint: RsLint,
    elementTitle: String = elementType
) : RsLocalInspectionTool() {
    val dispName = elementTitle + " naming convention"
    override fun getDisplayName() = dispName

    fun inspect(id: PsiElement?, holder: ProblemsHolder, fix: Boolean = true) {
        if (id == null) return
        val (isOk, suggestedName) = checkName(id.text)
        if (isOk || suggestedName == null || lint.levelFor(id) == RsLintLevel.ALLOW) return

        val fixEl = id.parent
        val fixes = if (fix && fixEl is PsiNamedElement) arrayOf(RenameFix(fixEl, suggestedName)) else emptyArray()
        holder.registerProblem(
            id,
            "$elementType `${id.text}` should have $styleName case name such as `$suggestedName`",
            *fixes)
    }

    abstract fun checkName(name: String): Pair<Boolean, String?>
}

/**
 * Checks if the name is CamelCase.
 */
open class RsCamelCaseNamingInspection(
    elementType: String,
    elementTitle: String = elementType
) : RsNamingInspection(elementType, "a camel", RsLint.NonCamelCaseTypes, elementTitle) {

    override fun checkName(name: String): Pair<Boolean, String?> {
        val str = name.trim('_')
        if (!str.isEmpty() && str[0].canStartWord && '_' !in str) {
            return Pair(true, null)
        }
        return Pair(false, if (str.isEmpty()) "CamelCase" else suggestName(name))
    }

    private fun suggestName(name: String): String {
        val result = StringBuilder(name.length)
        var wasUnderscore = true
        var startWord = true
        for (char in name) {
            when {
                char == '_' -> wasUnderscore = true
                wasUnderscore || startWord && char.canStartWord -> {
                    result.append(char.toUpperCase())
                    wasUnderscore = false
                    startWord = false
                }
                else -> {
                    startWord = char.isLowerCase()
                    result.append(char.toLowerCase())
                }
            }
        }
        return if (result.isEmpty()) "CamelCase" else result.toString()
    }

    private val Char.canStartWord: Boolean get() = isUpperCase() || isDigit()
}

/**
 * Checks if the name is snake_case.
 */
open class RsSnakeCaseNamingInspection(elementType: String) : RsNamingInspection(elementType, "a snake", RsLint.NonSnakeCase) {
    override fun checkName(name: String): Pair<Boolean, String?> {
        val str = name.trim('_')
        if (!str.isEmpty() && str.all { !it.isLetter() || it.isLowerCase() }) {
            return Pair(true, null)
        }
        return Pair(false, if (str.isEmpty()) "snake_case" else name.toSnakeCase(false))
    }
}

/**
 * Checks if the name is UPPER_CASE.
 */
open class RsUpperCaseNamingInspection(elementType: String) : RsNamingInspection(elementType, "an upper", RsLint.NonUpperCaseGlobals) {
    override fun checkName(name: String): Pair<Boolean, String?> {
        val str = name.trim('_')
        if (!str.isEmpty() && str.all { !it.isLetter() || it.isUpperCase() }) {
            return Pair(true, null)
        }
        return Pair(false, if (str.isEmpty()) "UPPER_CASE" else name.toSnakeCase(true))
    }
}

fun String.toSnakeCase(upper: Boolean): String {
    val result = StringBuilder(length + 3)     // 3 is a reasonable margin for growth when `_`s are added
    result.append(takeWhile { it == '_' || it == '\'' })    // Preserve prefix
    var firstPart = true
    drop(result.length).splitToSequence('_').forEach pit@ { part ->
        if (part.isEmpty()) return@pit
        if (!firstPart) {
            result.append('_')
        }
        firstPart = false
        var newWord = false
        var firstWord = true
        part.forEach { char ->
            if (newWord && char.isUpperCase()) {
                if (!firstWord) {
                    result.append('_')
                }
                newWord = false
            } else {
                newWord = char.isLowerCase()
            }
            result.append(if (upper) char.toUpperCase() else char.toLowerCase())
            firstWord = false
        }
    }
    return result.toString()
}

//
// Concrete inspections
//

class RsArgumentNamingInspection : RsSnakeCaseNamingInspection("Argument") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitPatBinding(el: RsPatBinding) {
                if (el.parent?.parent is RsValueParameter) {
                    inspect(el.identifier, holder)
                }
            }
        }
}

class RsConstNamingInspection : RsUpperCaseNamingInspection("Constant") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitConstant(el: RsConstant) {
                if (el.kind == RsConstantKind.CONST) {
                    inspect(el.identifier, holder)
                }
            }
        }
}

class RsStaticConstNamingInspection : RsUpperCaseNamingInspection("Static constant") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitConstant(el: RsConstant) {
                if (el.kind != RsConstantKind.CONST) {
                    inspect(el.identifier, holder)
                }
            }
        }
}

class RsEnumNamingInspection : RsCamelCaseNamingInspection("Type", "Enum") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitEnumItem(el: RsEnumItem) = inspect(el.identifier, holder)
        }
}

class RsEnumVariantNamingInspection : RsCamelCaseNamingInspection("Enum variant") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitEnumVariant(el: RsEnumVariant) = inspect(el.identifier, holder)
        }
}

class RsFunctionNamingInspection : RsSnakeCaseNamingInspection("Function") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitFunction(el: RsFunction) {
                if (el.owner is RsFunctionOwner.Free) {
                    inspect(el.identifier, holder)
                }
            }
        }
}

class RsMethodNamingInspection : RsSnakeCaseNamingInspection("Method") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitFunction(el: RsFunction) = when (el.owner) {
                is RsFunctionOwner.Trait, is RsFunctionOwner.Impl -> inspect(el.identifier, holder)
                else -> Unit
            }
        }
}

class RsLifetimeNamingInspection : RsSnakeCaseNamingInspection("Lifetime") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitLifetimeParameter(el: RsLifetimeParameter) = inspect(el.quoteIdentifier, holder)
        }
}

class RsMacroNamingInspection : RsSnakeCaseNamingInspection("Macro") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitMacroDefinition(el: RsMacroDefinition) = inspect(el.nameIdentifier, holder)
        }
}

class RsModuleNamingInspection : RsSnakeCaseNamingInspection("Module") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitModDeclItem(el: RsModDeclItem) = inspect(el.identifier, holder)
            override fun visitModItem(el: RsModItem) = inspect(el.identifier, holder)
        }
}

class RsStructNamingInspection : RsCamelCaseNamingInspection("Type", "Struct") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitStructItem(el: RsStructItem) = inspect(el.identifier, holder)
        }
}

class RsFieldNamingInspection : RsSnakeCaseNamingInspection("Field") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitFieldDecl(el: RsFieldDecl) = inspect(el.identifier, holder)
        }
}

class RsTraitNamingInspection : RsCamelCaseNamingInspection("Trait") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitTraitItem(el: RsTraitItem) = inspect(el.identifier, holder)
        }
}

class RsTypeAliasNamingInspection : RsCamelCaseNamingInspection("Type", "Type alias") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitTypeAlias(el: RsTypeAlias) {
                if (el.owner is RsTypeAliasOwner.Free) {
                    inspect(el.identifier, holder)
                }
            }
        }
}

class RsAssocTypeNamingInspection : RsCamelCaseNamingInspection("Type", "Associated type") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitTypeAlias(el: RsTypeAlias) {
                if (el.owner is RsTypeAliasOwner.Trait) {
                    inspect(el.identifier, holder, false)
                }
            }
        }
}


class RsTypeParameterNamingInspection : RsCamelCaseNamingInspection("Type parameter") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitTypeParameter(el: RsTypeParameter) = inspect(el.identifier, holder)
        }
}

class RsVariableNamingInspection : RsSnakeCaseNamingInspection("Variable") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitPatBinding(el: RsPatBinding) {
                val pattern = PsiTreeUtil.getTopmostParentOfType(el, RsPat::class.java) ?: return
                when (pattern.parent) {
                    is RsLetDecl -> inspect(el.identifier, holder)
                }
            }
        }
}
