/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.lang.core.psi.RsConstant
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsPathExpr
import org.rust.lang.core.psi.RsPathType
import org.rust.lang.core.psi.ext.RsConstContextKind
import org.rust.lang.core.psi.ext.classifyConstContext
import org.rust.lang.core.psi.ext.isConst
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder

/**
 * Inspection that detects the E0013 error.
 */
class RsConstReferStaticInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) = object : RsWithMacrosInspectionVisitor() {
        override fun visitPathExpr(pathExpr: RsPathExpr) {
            val constContext = pathExpr.classifyConstContext
            if (constContext != null) {
                checkPathInConstContext(holder, pathExpr.path, constContext)
            }
            super.visitPathExpr(pathExpr)
        }

        override fun visitPathType(o: RsPathType) {
            checkPathInConstContext(holder, o.path, RsConstContextKind.ConstGenericArgument)
            super.visitPathType(o)
        }
    }

    private fun checkPathInConstContext(holder: RsProblemsHolder, path: RsPath, constContext: RsConstContextKind) {
        val ref = path.reference?.resolve() as? RsConstant ?: return
        if (!ref.isConst) {
            RsDiagnostic.ConstItemReferToStaticError(path, constContext).addToHolder(holder)
        }
    }
}
