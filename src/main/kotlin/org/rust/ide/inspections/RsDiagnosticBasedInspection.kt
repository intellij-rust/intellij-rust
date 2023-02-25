/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsInferenceContextOwner
import org.rust.lang.core.types.selfInferenceResult
import org.rust.lang.utils.addToHolder

abstract class RsDiagnosticBasedInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor = object : RsWithMacrosInspectionVisitor() {
        override fun visitFunction2(o: RsFunction) = collectDiagnostics(holder, o)
        override fun visitConstant2(o: RsConstant) = collectDiagnostics(holder, o)
        override fun visitConstParameter(o: RsConstParameter) = collectDiagnostics(holder, o)
        override fun visitArrayType(o: RsArrayType) = collectDiagnostics(holder, o)
        override fun visitPath(o: RsPath) = collectDiagnostics(holder, o)
        override fun visitVariantDiscriminant(o: RsVariantDiscriminant) = collectDiagnostics(holder, o)
    }

    private fun collectDiagnostics(holder: RsProblemsHolder, element: RsInferenceContextOwner) {
        for (it in element.selfInferenceResult.diagnostics) {
            if (it.inspectionClass == javaClass) it.addToHolder(holder)
        }
    }
}

class RsExperimentalChecksInspection : RsDiagnosticBasedInspection()

class RsTypeCheckInspection : RsDiagnosticBasedInspection()
