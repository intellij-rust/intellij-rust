/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.spelling

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.spellchecker.generator.SpellCheckerDictionaryGenerator
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsConstParameterImplMixin
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsNameIdentifierOwner
import java.util.*

class RsSpellCheckerDictionaryGenerator(project: Project, outputFolder: String) :
    SpellCheckerDictionaryGenerator(project, outputFolder, "rust") {

    override fun processFile(file: PsiFile, seenNames: HashSet<String>) {
        file.accept(object : RsRecursiveVisitor() {
            override fun visitElement(element: RsElement) {
                when (element) {
                    is RsConstParameterImplMixin,
                    is RsLabelDecl,
                    is RsLifetime,
                    is RsLifetimeParameter,
                    is RsMacroBinding,
                    is RsPatBinding,
                    is RsTypeParameter -> Unit
                    is RsNameIdentifierOwner -> processLeafsNames(element, seenNames)
                    else -> Unit
                }
                super.visitElement(element)
            }
        })
    }
}
