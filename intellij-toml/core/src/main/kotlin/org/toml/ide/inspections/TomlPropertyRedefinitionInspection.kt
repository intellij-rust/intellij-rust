/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.toml.lang.psi.*

class TomlPropertyRedefinitionInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val elements = hashSetOf<List<String>>()

        return object : TomlVisitor() {
            override fun visitElement(element: TomlElement) {
                if (element !is TomlKey) return

                val path = element.path
                if (path in elements) {
                    holder.registerProblem(element, "Property redefinition is not allowed")
                } else {
                    elements.add(path)
                }
            }
        }
    }
}

val TomlElement.path: List<String>
    get() {
        var current = when (this) {
            is TomlKeyValue -> this.key.segments.map { it.text }
            is TomlTable -> this.header.key?.segments?.map { it.text }
            else -> null
        } ?: emptyList()

        if (this.parent !is TomlFile) {
            val parentPath = (this.parent as TomlElement).path
            current = parentPath + current
        }

        return current
    }
