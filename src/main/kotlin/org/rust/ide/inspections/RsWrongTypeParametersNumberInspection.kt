package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.rust.lang.core.psi.RsBaseType
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.RsGenericDeclaration

/**
 * Inspection that detects E0243/E0244 errors.
 */
class RsWrongTypeParametersNumberInspection : RsLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitBaseType(type: RsBaseType) {
                // Don't apply generic declaration checks to Fn-traits and `Self`
                if (type.path?.valueParameterList != null) return
                if (type.path?.cself != null) return

                val paramsDecl = type.path?.reference?.resolve() as? RsGenericDeclaration ?: return
                val expectedRequiredParams = paramsDecl.typeParameterList?.typeParameterList?.filter { it.eq == null }?.size ?: 0
                val expectedTotalParams = paramsDecl.typeParameterList?.typeParameterList?.size ?: 0
                val actualArgs = type.path?.typeArgumentList?.typeReferenceList?.size ?: 0
                val (code, expectedText) = when {
                    actualArgs < expectedRequiredParams -> ("E0243" to if (expectedRequiredParams != expectedTotalParams) "at least $expectedRequiredParams" else "$expectedTotalParams")
                    actualArgs > expectedTotalParams -> ("E0244" to if (expectedRequiredParams != expectedTotalParams) "at most $expectedTotalParams" else "$expectedTotalParams")
                    else -> null
                } ?: return
                holder.registerProblem(type, "Wrong number of type parameters: expected $expectedText, found $actualArgs [$code]")
            }
        }

}
