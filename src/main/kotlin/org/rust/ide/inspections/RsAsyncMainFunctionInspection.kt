/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.lang.core.psi.KnownProcMacroKind
import org.rust.lang.core.psi.ProcMacroAttribute
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.isMain
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder

class RsAsyncMainFunctionInspection: RsLocalInspectionTool() {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitFunction2(o: RsFunction) {
                val async = o.node.findChildByType(RsElementTypes.ASYNC)?.psi
                if (o.isMain && async != null) {
                    val hardcodedProMacros = ProcMacroAttribute.getHardcodedProcMacroAttributes(o)
                    val hasAsyncMainMacro = hardcodedProMacros.any { it == KnownProcMacroKind.ASYNC_MAIN }
                    if (!hasAsyncMainMacro) {
                        RsDiagnostic.AsyncMainFunction(async, o).addToHolder(holder)
                    }
                }
            }
        }
}
