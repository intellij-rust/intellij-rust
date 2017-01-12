package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.impl.mixin.*
import org.rust.lang.core.psi.queryAttributes
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.types.RustStructOrEnumTypeBase
import org.rust.lang.core.types.util.resolvedType

class RsSelfConventionInspection : RsLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitFunction(m: RsFunction) {
                // Should this handle traits as well perhaps?
                if (m.role != RsFunctionRole.IMPL_METHOD) return

                val convention = SELF_CONVENTIONS.find { m.identifier.text.startsWith(it.prefix) } ?: return
                if (m.selfType in convention.selfTypes) return
                if (m.selfType == SelfType.SELF && m.isOwnerCopyable()) return
                holder.registerProblem(m.selfParameter ?: m.identifier, convention)
            }
        }

    private fun RsFunction.isOwnerCopyable(): Boolean {
        val implBlock = parentOfType<RsImplItem>() ?: return false
        val owner = implBlock.type?.resolvedType as? RustStructOrEnumTypeBase ?: return false
        return owner.item.queryAttributes.hasAttributeWithArg("derive", "Copy")
    }

    private companion object {
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

private val RsFunction.selfType: SelfType get() {
    val self = selfParameter
    return when {
        self == null -> SelfType.NO_SELF
        self.isMut -> SelfType.REF_MUT_SELF
        self.isRef -> SelfType.REF_SELF
        else -> SelfType.SELF
    }
}

data class SelfConvention(val prefix: String, val selfTypes: Collection<SelfType>)

private fun ProblemsHolder.registerProblem(element: PsiElement, convention: SelfConvention) {
    val selfTypes = convention.selfTypes.map { it.description }.joinToString(" or ")

    val description = "methods called `${convention.prefix}*` usually take $selfTypes; " +
        "consider choosing a less ambiguous name"

    registerProblem(element, description)
}
