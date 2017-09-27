/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.types.inference
import org.rust.lang.utils.addToHolder

class RsExperimentalChecksInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitFunction(fn: RsFunction) {
            for (it in fn.inference.diagnostics) {
                if (it.experimental) it.addToHolder(holder)
            }
        }
    }
}
