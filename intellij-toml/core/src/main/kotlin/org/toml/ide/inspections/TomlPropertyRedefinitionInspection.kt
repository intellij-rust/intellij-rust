/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.parentOfType
import org.toml.lang.psi.*

class TomlPropertyRedefinitionInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val elements = mutableListOf<List<String>>()

        return object : TomlVisitor() {
            override fun visitElement(element: TomlElement) {
                if (element !is TomlKey) return

                val path = element.path
                // Check if `elements` already have a path starting with current `path`
                if (elements.find { it.size >= path.size && it.zip(path).all { it.first == it.second } } != null) {
                    holder.registerProblem(element, "Property redefinition is not allowed")
                } else {
                    // There is no need to remember array table paths, as they naturally can be defined multiple times
                    if (element.parentOfType<TomlArrayTable>() == null) {
                        elements.add(path)
                    }
                }
            }
        }
    }
}

private val TomlElement.path: List<String>
    get() {
        var current = when (this) {
            is TomlKeyValue -> this.key.segments.map { it.text }
            is TomlHeaderOwner -> this.header.key?.segments?.map { it.text }
            else -> null
        } ?: emptyList()

        if (this.parent !is TomlFile) {
            val parentPath = (this.parent as TomlElement).path
            current = parentPath + current
        }

        return current
    }
