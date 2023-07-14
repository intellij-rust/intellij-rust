/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.ext.childrenOfType
import org.rust.toml.RsTomlBundle
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlKeyValueOwner
import org.toml.lang.psi.TomlVisitor

class TomlDuplicatedKeyInspection : TomlLocalInspectionToolBase() {
    override fun buildVisitorInternal(holder: ProblemsHolder, isOnTheFly: Boolean): TomlVisitor {
        return object : TomlVisitor() {
            override fun visitKeyValueOwner(element: TomlKeyValueOwner) {
                val keyValues = element.entries
                highlightDuplicates(keyValues)
            }

            override fun visitFile(file: PsiFile) {
                // Top-level key/values do not have a KeyValueOwner
                val keyValues = file.childrenOfType<TomlKeyValue>()
                highlightDuplicates(keyValues)
            }

            private fun highlightDuplicates(entries: List<TomlKeyValue>) {
                entries
                    .groupBy { it.key.segments.mapNotNull { segment -> segment.name }.joinToString(".") }
                    .filter { it.value.size > 1 }
                    .values
                    .forEach { tomlKeyValues -> tomlKeyValues.forEach { keyValue -> holder.registerProblem(
                        keyValue.key,
                        RsTomlBundle.message("inspection.duplicated.key.problem")
                    ) } }
            }
        }
    }
}
