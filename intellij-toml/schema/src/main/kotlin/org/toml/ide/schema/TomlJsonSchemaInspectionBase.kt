/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.schema

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import org.toml.lang.psi.TomlFile

abstract class TomlJsonSchemaInspectionBase : LocalInspectionTool() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        val file = holder.file;
        if (file !is TomlFile) return PsiElementVisitor.EMPTY_VISITOR

        val roots = TomlJsonPsiWalker.INSTANCE.getRoots(file)
        if (roots.isEmpty()) return PsiElementVisitor.EMPTY_VISITOR

        val service = JsonSchemaService.Impl.get(file.project)
        val virtualFile = file.viewProvider.virtualFile
        if (!service.isApplicableToFile(virtualFile)) return PsiElementVisitor.EMPTY_VISITOR

        val rootSchema = service.getSchemaObject(file) ?: return PsiElementVisitor.EMPTY_VISITOR

        return doBuildVisitor(holder, session, roots, rootSchema)
    }

    protected abstract fun doBuildVisitor(
        holder: ProblemsHolder,
        session: LocalInspectionToolSession,
        roots: Collection<PsiElement>,
        rootSchema: JsonSchemaObject
    ): PsiElementVisitor
}
