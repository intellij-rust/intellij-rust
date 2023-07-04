/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.rust.toml.RsTomlBundle
import org.toml.lang.psi.TomlArray
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlVisitor
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind

class TomlInvalidKeywordSegmentInspection : TomlLocalInspectionToolBase() {
    override fun buildVisitorInternal(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor? {
        return object : TomlVisitor() {
            override fun visitKeyValue(element: TomlKeyValue) {
                if (element.key.segments.singleOrNull()?.name != "keywords") return;
                val keywordsArray = element.value as? TomlArray ?: return
                val keywords = keywordsArray.elements
                if (keywords.size > 5) {
                    holder.registerProblem(keywordsArray, RsTomlBundle.message("rust.too.many.keywords"))
                }
                for (keyword in keywords) {
                    if (keyword is TomlLiteral) {
                        val kind = keyword.kind as? TomlLiteralKind.String ?: continue
                        if (kind.value?.isValidKeyword() == false) {
                            holder.registerProblem(keyword, RsTomlBundle.message("rust.invalid.keyword"))
                        }
                    }
                }
            }
        }
    }
}

private fun String.isValidKeyword(): Boolean {
    if (isEmpty() || length > 20) return false
    if (!first().isLetter()) return false;
    return all { it.isLetterOrDigit() || it == '_' || it == '-' }
}
