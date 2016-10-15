package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.rust.lang.core.psi.*
import org.rust.lang.core.resolve.ref.RustReference

/**
 * Detects usage of deprecated API
 */
class RustDeprecatedInspection : RustLocalInspectionTool() {
    override fun getDisplayName(): String = "Deprecated API usage"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : RustElementVisitor() {
            override fun visitMethodCallExpr(el: RustMethodCallExprElement) = inspectReference(el.reference, el.identifier, holder)
            override fun visitPathExpr(el: RustPathExprElement) = inspectReference(el.path.reference, el, holder)
            override fun visitPathType(el: RustPathTypeElement) = inspectReference(el.path?.reference, el.path, holder)
            override fun visitStructExpr(el: RustStructExprElement) = inspectReference(el.path.reference, el.path, holder)
            override fun visitStructExprField(el: RustStructExprFieldElement) = inspectReference(el.reference, el.identifier, holder)
            override fun visitTraitRef(el: RustTraitRefElement) = inspectReference(el.path.reference, el, holder)
            override fun visitUseGlob(el: RustUseGlobElement) = inspectReference(el.reference, el, holder)
            override fun visitUseItem(el: RustUseItemElement) {
                if (el.useGlobList == null) {
                    inspectReference(el.path?.reference, el.path, holder)
                }
            }
        }

    fun inspectReference(ref: RustReference?, el: PsiElement?, holder: ProblemsHolder) {
        if (ref == null || el == null) return
        val refEl = ref.resolve() ?: return
        val (deprecated, deprTitle) = refEl.findDeprecation()
        if (deprecated) {
            holder.registerProblem(el, "$deprTitle is deprecated", ProblemHighlightType.LIKE_DEPRECATED)
        }
    }

    /**
     * Detects if element is deprecated and returns the title of the exact tree node
     * that causes deprecation.
     */
    private fun PsiElement.findDeprecation(): Pair<Boolean, String?> {
        if (this is RustOuterAttributeOwner && (findOuterAttr("deprecated") != null || findOuterAttr("rustc_deprecated") != null)) {
            return Pair(true, getTitle())
        }
        if (this is RustInnerAttributeOwner && (findInnerAttr("deprecated") != null || findInnerAttr("rustc_deprecated") != null)) {
            return Pair(true, getTitle())
        }
        if (parent != null) {
            return parent.findDeprecation()
        }
        return Pair(false, null)
    }

    private fun PsiElement.getTitle(): String = when (this) {
        is RustConstItemElement -> "Constant `$name`"
        is RustEnumItemElement -> "Enum `$name`"
        is RustEnumVariantElement -> "Enum variant `$name`"
        is RustFnItemElement -> "Function `$name`"
        is RustFieldDeclElement -> "Field `$name`"
        is RustImplMethodMemberElement -> "Method `$name`"
        is RustModItemElement -> "Module `$name`"
        is RustStaticItemElement -> "Static constant `$name`"
        is RustStructItemElement -> "Type `$name`"
        is RustTraitItemElement -> "Trait `$name`"
        is RustTypeItemElement -> "Type `$name`"
        is RustNamedElement -> "`$name`"
        else -> "Item"
    }
}
