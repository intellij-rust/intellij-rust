/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.psi.PsiElement
import org.rust.ide.presentation.render
import org.rust.lang.core.types.ty.Ty

abstract class ConvertToTyFix : LocalQuickFixAndIntentionActionOnPsiElement {

    private val tyName: String
    private val convertSubject: String

    constructor(expr: PsiElement, tyName: String, convertSubject: String): super(expr) {
        this.tyName = tyName
        this.convertSubject = convertSubject
    }

    constructor(expr: PsiElement, ty: Ty, convertSubject: String) :
        this(expr, ty.render(skipUnchangedDefaultTypeArguments = true), convertSubject)

    override fun getFamilyName(): String = "Convert to type"
    override fun getText(): String = "Convert to $tyName using $convertSubject"
}
