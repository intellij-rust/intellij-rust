/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.inlineTypeAlias

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.lang.Language
import com.intellij.lang.refactoring.InlineActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.RsLanguage
import org.rust.lang.core.macros.isExpandedFromMacro
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.openapiext.isUnitTestMode

class RsInlineTypeAliasHandler : InlineActionHandler() {

    override fun isEnabledForLanguage(language: Language): Boolean = language == RsLanguage

    override fun canInlineElement(element: PsiElement): Boolean =
        element is RsTypeAlias
            && element.name != null
            // `type Foo: X = ...;`
            && element.typeParamBounds == null
            && element.typeReference != null
            && element.parent.let { it is RsMod || it is RsBlock }
            && !element.isExpandedFromMacro

    override fun inlineElement(project: Project, editor: Editor, element: PsiElement) {
        val typeAlias = element as RsTypeAlias
        val reference = TargetElementUtil.findReference(editor, editor.caretModel.offset) as? RsReference
        if (!isUnitTestMode) {
            val dialog = RsInlineTypeAliasDialog(typeAlias, reference)
            dialog.show()
        } else {
            val processor = RsInlineTypeAliasProcessor(project, typeAlias, reference, inlineThisOnly = false)
            processor.run()
        }
    }
}
