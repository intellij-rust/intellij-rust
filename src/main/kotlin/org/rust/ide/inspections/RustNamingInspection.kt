package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.ide.inspections.fixes.RenameFix
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.mixin.*

/**
 * Base class for naming inspections. Implements the core logic of checking names
 * and registering problems.
 */
abstract class RustNamingInspection(
    val elementType: String,
    val styleName: String,
    val lint: RustLint,
    elementTitle: String = elementType
) : RustLocalInspectionTool() {
    val dispName = elementTitle + " naming convention"
    override fun getDisplayName() = dispName

    fun inspect(id: PsiElement?, holder: ProblemsHolder, fix: Boolean = true) {
        if (id == null) return
        val (isOk, suggestedName) = checkName(id.text)
        if (isOk || suggestedName == null || lint.levelFor(id) == RustLintLevel.ALLOW) return

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
open class RustCamelCaseNamingInspection(
    elementType: String,
    elementTitle: String = elementType
) : RustNamingInspection(elementType, "a camel", RustLint.NonCamelCaseTypes, elementTitle) {

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
open class RustSnakeCaseNamingInspection(elementType: String) : RustNamingInspection(elementType, "a snake", RustLint.NonSnakeCase) {
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
open class RustUpperCaseNamingInspection(elementType: String) : RustNamingInspection(elementType, "an upper", RustLint.NonUpperCaseGlobals) {
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

class RustArgumentNamingInspection : RustSnakeCaseNamingInspection("Argument") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitPatBinding(el: RsPatBinding) {
                if (el.parent?.parent is RsValueParameter) {
                    inspect(el.identifier, holder)
                }
            }
        }
}

class RustConstNamingInspection : RustUpperCaseNamingInspection("Constant") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitConstant(el: RsConstant) {
                if (el.kind == RustConstantKind.CONST) {
                    inspect(el.identifier, holder)
                }
            }
        }
}

class RustStaticConstNamingInspection : RustUpperCaseNamingInspection("Static constant") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitConstant(el: RsConstant) {
                if (el.kind != RustConstantKind.CONST) {
                    inspect(el.identifier, holder)
                }
            }
        }
}

class RustEnumNamingInspection : RustCamelCaseNamingInspection("Type", "Enum") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitEnumItem(el: RsEnumItem) = inspect(el.identifier, holder)
        }
}

class RustEnumVariantNamingInspection : RustCamelCaseNamingInspection("Enum variant") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitEnumVariant(el: RsEnumVariant) = inspect(el.identifier, holder)
        }
}

class RustFunctionNamingInspection : RustSnakeCaseNamingInspection("Function") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitFunction(el: RsFunction) {
                if (el.role == RustFunctionRole.FREE) {
                    inspect(el.identifier, holder)
                }
            }
        }
}

class RustMethodNamingInspection : RustSnakeCaseNamingInspection("Method") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitFunction(el: RsFunction) = when (el.role) {
                RustFunctionRole.TRAIT_METHOD,
                RustFunctionRole.IMPL_METHOD -> inspect(el.identifier, holder)
                else -> Unit
            }
        }
}

class RustLifetimeNamingInspection : RustSnakeCaseNamingInspection("Lifetime") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitLifetimeParameter(el: RsLifetimeParameter) = inspect(el, holder, false)
        }
}

class RustMacroNamingInspection : RustSnakeCaseNamingInspection("Macro") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitMacroDefinition(el: RsMacroDefinition) = inspect(el.identifier, holder, false)
        }
}

class RustModuleNamingInspection : RustSnakeCaseNamingInspection("Module") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitModDeclItem(el: RsModDeclItem) = inspect(el.identifier, holder)
            override fun visitModItem(el: RsModItem) = inspect(el.identifier, holder)
        }
}

class RustStructNamingInspection : RustCamelCaseNamingInspection("Type", "Struct") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitStructItem(el: RsStructItem) = inspect(el.identifier, holder)
        }
}

class RustFieldNamingInspection : RustSnakeCaseNamingInspection("Field") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitFieldDecl(el: RsFieldDecl) = inspect(el.identifier, holder)
        }
}

class RustTraitNamingInspection : RustCamelCaseNamingInspection("Trait") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitTraitItem(el: RsTraitItem) = inspect(el.identifier, holder)
        }
}

class RustTypeAliasNamingInspection : RustCamelCaseNamingInspection("Type", "Type alias") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitTypeAlias(el: RsTypeAlias) {
                if (el.role == RustTypeAliasRole.FREE) {
                    inspect(el.identifier, holder)
                }
            }
        }
}

class RustAssocTypeNamingInspection : RustCamelCaseNamingInspection("Type", "Associated type") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitTypeAlias(el: RsTypeAlias) {
                if (el.role == RustTypeAliasRole.TRAIT_ASSOC_TYPE) {
                    inspect(el.identifier, holder, false)
                }
            }
        }
}


class RustTypeParameterNamingInspection : RustCamelCaseNamingInspection("Type parameter") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitTypeParameter(el: RsTypeParameter) = inspect(el.identifier, holder)
        }
}

class RustVariableNamingInspection : RustSnakeCaseNamingInspection("Variable") {
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
