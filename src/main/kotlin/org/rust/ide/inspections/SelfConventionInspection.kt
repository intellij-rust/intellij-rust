package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.rust.lang.core.psi.RustImplMethod
import org.rust.lang.core.psi.RustVisitor

class SelfConventionInspection : RustLocalInspectionTool() {

    override fun getDisplayName() = "Self Convention"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : RustVisitor() {
            override fun visitImplMethod(m: RustImplMethod) {
                val convention = SELF_CONVENTIONS.find { m.identifier.text.startsWith(it.prefix) } ?: return
                if (m.selfType !in convention.selfTypes) {
                    holder.registerProblem(m.selfArgument ?: m.identifier, convention)
                }
            }
        }
    }

    companion object {
        val SELF_CONVENTIONS = listOf(
            SelfConvention("as_", listOf(SelfType.REF_SELF, SelfType.REF_MUT_SELF)),
            SelfConvention("from_", listOf(SelfType.NO_SELF)),
            SelfConvention("into_", listOf(SelfType.SELF)),
            SelfConvention("is_", listOf(SelfType.REF_SELF, SelfType.NO_SELF)),
            SelfConvention("to_", listOf(SelfType.REF_SELF))
        )
    }
}

enum class SelfType(val description: String) {
    NO_SELF("no self"),
    SELF("self by value"),
    REF_SELF("self by reference"),
    REF_MUT_SELF("self by mutable reference");
}

private val RustImplMethod.selfType: SelfType get() {
    val self = selfArgument
    return when {
        self == null -> SelfType.NO_SELF
        self.and == null -> SelfType.SELF
        self.mut == null -> SelfType.REF_SELF
        else -> SelfType.REF_MUT_SELF
    }
}

data class SelfConvention(val prefix: String, val selfTypes: Collection<SelfType>)

private fun ProblemsHolder.registerProblem(element: PsiElement, convention: SelfConvention) {
    val selfTypes = convention.selfTypes.map { it.description }.joinToString(" or ")

    val description = "methods called `${convention.prefix}*` usually take $selfTypes; " +
        "consider choosing a less ambiguous name"

    registerProblem(element, description)
}
