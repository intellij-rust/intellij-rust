/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.ide.annotator.fixes.EncloseExprInBracesFix
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder

/**
 * Inspection that detects the E0747 error.
 */
class RsWrongGenericArgumentsOrderInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsVisitor() {
            override fun visitMethodCall(methodCall: RsMethodCall) = checkGenericArguments(holder, methodCall)
            override fun visitPath(path: RsPath) {
                if (!isPathValid(path)) return
                checkGenericArguments(holder, path)
            }
        }

    // Don't apply generic declaration checks to Fn-traits and `Self`
    private fun isPathValid(path: RsPath?): Boolean = path?.valueParameterList == null && path?.cself == null

    private fun checkGenericArguments(holder: RsProblemsHolder, element: RsElement) {
        val (actualArguments, declaration) = getTypeArgumentsAndDeclaration(element) ?: return
        if (actualArguments == null) return
        val parameterList = declaration.typeParameterList ?: return

        val params = parameterList.stubChildrenOfType<RsGenericParameter>().filterNot { it is RsLifetimeParameter }
        val args = actualArguments.stubChildrenOfType<RsElement>().filter { it is RsTypeReference || it is RsExpr }

        val typeArguments = actualArguments.typeArguments
        val constArguments = actualArguments.constArguments

        fun kindName(arg: RsElement): String = when (arg) {
            in typeArguments -> "Type"
            in constArguments -> "Constant"
            else -> error("impossible")
        }

        for ((param, arg) in params.zip(args)) {
            val text = when {
                param is RsTypeParameter && arg !in typeArguments -> "${kindName(arg)} provided when a type was expected"
                param is RsConstParameter && arg !in constArguments -> "${kindName(arg)} provided when a constant was expected"
                else -> continue
            }
            val fixes = if (param is RsConstParameter && arg is RsTypeReference) {
                listOf(EncloseExprInBracesFix(arg))
            } else {
                emptyList()
            }
            RsDiagnostic.WrongOrderOfGenericArguments(arg, text, fixes).addToHolder(holder)
        }
    }
}
