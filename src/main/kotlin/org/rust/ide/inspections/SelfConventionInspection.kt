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
            override fun visitImplMethod(o: RustImplMethod) {
                val convention = SELF_CONVENTIONS.find { o.identifier.text.startsWith(it.prefix) } ?: return
                val selfType = SelfType.from(o.and, o.mut, o.self)
                if (selfType !in convention.selfTypes) {
                    holder.registerProblem(o.self ?: o.identifier, convention)
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

    companion object {
        fun from(and: PsiElement?, mut: PsiElement?, self: PsiElement?) =
            when {
                self == null -> NO_SELF
                and == null  -> SELF
                mut == null  -> REF_SELF
                else         -> REF_MUT_SELF
            }
    }
}

data class SelfConvention(val prefix: String, val selfTypes: Collection<SelfType>)

private fun ProblemsHolder.registerProblem(element: PsiElement, convention: SelfConvention) {
    val selfTypes = convention.selfTypes.map { it.description }.joinToString(" or ")

    val description = "methods called `${convention.prefix}*` usually take $selfTypes; " +
        "consider choosing a less ambiguous name"

    registerProblem(element, description)
}
