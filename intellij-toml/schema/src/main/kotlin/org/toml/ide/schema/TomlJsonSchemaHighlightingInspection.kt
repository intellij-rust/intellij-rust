/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.schema

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.impl.JsonComplianceCheckerOptions
import com.jetbrains.jsonSchema.impl.JsonSchemaComplianceChecker
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import org.toml.lang.psi.TomlElement
import org.toml.lang.psi.TomlVisitor

class TomlJsonSchemaHighlightingInspection : TomlJsonSchemaInspectionBase() {
    override fun doBuildVisitor(
        holder: ProblemsHolder,
        session: LocalInspectionToolSession,
        roots: Collection<PsiElement>,
        rootSchema: JsonSchemaObject
    ): PsiElementVisitor {
        val options = JsonComplianceCheckerOptions(false)

        return object : TomlVisitor() {
            override fun visitElement(element: TomlElement) {
                if (!roots.contains(element)) return
                val walker = JsonLikePsiWalker.getWalker(element, rootSchema) ?: return

                JsonSchemaComplianceChecker(rootSchema, holder, walker, session, options).annotate(element)
            }
        }
    }
}
