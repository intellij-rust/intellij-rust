package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.ide.inspections.fixes.RenameFix
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustParameterElementImpl

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

private fun String.toSnakeCase(upper: Boolean): String {
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
        object : RustElementVisitor() {
            override fun visitPatBinding(el: RustPatBindingElement) {
                if (el.parent?.parent is RustParameterElementImpl) {
                    inspect(el.identifier, holder)
                }
            }
        }
}

class RustAssocTypeNamingInspection : RustCamelCaseNamingInspection("Type", "Associated type") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RustElementVisitor() {
            override fun visitTraitTypeMember(el: RustTraitTypeMemberElement) = inspect(el.identifier, holder, false)
        }
}

class RustConstNamingInspection : RustUpperCaseNamingInspection("Constant") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RustElementVisitor() {
            override fun visitConstItem(el: RustConstItemElement) = inspect(el.identifier, holder)
        }
}

class RustEnumNamingInspection : RustCamelCaseNamingInspection("Type", "Enum") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RustElementVisitor() {
            override fun visitEnumItem(el: RustEnumItemElement) = inspect(el.identifier, holder)
        }
}

class RustEnumVariantNamingInspection : RustCamelCaseNamingInspection("Enum variant") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RustElementVisitor() {
            override fun visitEnumVariant(el: RustEnumVariantElement) = inspect(el.identifier, holder)
        }
}

class RustFunctionNamingInspection : RustSnakeCaseNamingInspection("Function") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RustElementVisitor() {
            override fun visitFnItem(el: RustFnItemElement) = inspect(el.identifier, holder)
        }
}

class RustLifetimeNamingInspection : RustSnakeCaseNamingInspection("Lifetime") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RustElementVisitor() {
            override fun visitLifetimeParam(el: RustLifetimeParamElement) = inspect(el, holder, false)
        }
}

class RustMacroNamingInspection : RustSnakeCaseNamingInspection("Macro") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RustElementVisitor() {
            override fun visitMacroDefinition(el: RustMacroDefinitionElement) = inspect(el.identifier, holder, false)
        }
}

class RustMethodNamingInspection : RustSnakeCaseNamingInspection("Method") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RustElementVisitor() {
            override fun visitTraitMethodMember(el: RustTraitMethodMemberElement) = inspect(el.identifier, holder, false)
            override fun visitImplMethodMember(el: RustImplMethodMemberElement) = inspect(el.identifier, holder)
        }
}

class RustModuleNamingInspection : RustSnakeCaseNamingInspection("Module") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RustElementVisitor() {
            override fun visitModDeclItem(el: RustModDeclItemElement) = inspect(el.identifier, holder)
            override fun visitModItem(el: RustModItemElement) = inspect(el.identifier, holder)
        }
}

class RustStaticConstNamingInspection : RustUpperCaseNamingInspection("Static constant") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RustElementVisitor() {
            override fun visitStaticItem(el: RustStaticItemElement) = inspect(el.identifier, holder)
        }
}

class RustStructNamingInspection : RustCamelCaseNamingInspection("Type", "Struct") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RustElementVisitor() {
            override fun visitStructItem(el: RustStructItemElement) = inspect(el.identifier, holder)
        }
}

class RustFieldNamingInspection : RustSnakeCaseNamingInspection("Field") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RustElementVisitor() {
            override fun visitFieldDecl(el: RustFieldDeclElement) = inspect(el.identifier, holder)
        }
}

class RustTraitNamingInspection : RustCamelCaseNamingInspection("Trait") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RustElementVisitor() {
            override fun visitTraitItem(el: RustTraitItemElement) = inspect(el.identifier, holder)
        }
}

class RustTypeAliasNamingInspection : RustCamelCaseNamingInspection("Type", "Type alias") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RustElementVisitor() {
            override fun visitTypeItem(el: RustTypeItemElement) = inspect(el.identifier, holder)
        }
}

class RustTypeParameterNamingInspection : RustCamelCaseNamingInspection("Type parameter") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RustElementVisitor() {
            override fun visitTypeParam(el: RustTypeParamElement) = inspect(el.identifier, holder)
        }
}

class RustVariableNamingInspection : RustSnakeCaseNamingInspection("Variable") {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RustElementVisitor() {
            override fun visitPatBinding(el: RustPatBindingElement) {
                val pattern = PsiTreeUtil.getTopmostParentOfType(el, RustPatElement::class.java) ?: return
                when (pattern.parent) {
                    is RustLetDeclElement -> inspect(el.identifier, holder)
                }
            }
        }
}
