package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.types.RustStructOrEnumTypeBase
import org.rust.lang.core.types.util.resolvedType

class RustSelfConventionInspection : RustLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RustElementVisitor() {
            override fun visitImplMethodMember(m: RustImplMethodMemberElement) {
                val convention = SELF_CONVENTIONS.find { m.identifier.text.startsWith(it.prefix) } ?: return
                if (m.selfType in convention.selfTypes) return
                if (m.selfType == SelfType.SELF && m.isOwnerCopyable()) return
                holder.registerProblem(m.parameters?.selfArgument ?: m.identifier, convention)
            }
        }

    private fun RustImplMethodMemberElement.isOwnerCopyable(): Boolean {
        val implBlock = parentOfType<RustImplItemElement>() ?: return false
        val owner = implBlock.type?.resolvedType as? RustStructOrEnumTypeBase ?: return false
        return owner.item.queryAttributes.hasMetaItem("derive", "Copy")
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

private val RustImplMethodMemberElement.selfType: SelfType get() {
    val self = parameters?.selfArgument
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
