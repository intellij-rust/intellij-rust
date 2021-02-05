/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.rust.toml.tomlPluginIsAbiCompatible

abstract class TomlLocalInspectionToolBase : LocalInspectionTool() {

    final override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!tomlPluginIsAbiCompatible()) return super.buildVisitor(holder, isOnTheFly)
        return buildVisitorInternal(holder, isOnTheFly) ?: super.buildVisitor(holder, isOnTheFly)
    }

    protected abstract fun buildVisitorInternal(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor?
}
