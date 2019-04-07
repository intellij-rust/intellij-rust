/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.CustomSuppressableInspectionTool
import com.intellij.codeInspection.SuppressIntentionAction
import com.intellij.psi.PsiElement

abstract class RsLintInspection : RsLocalInspectionTool(), CustomSuppressableInspectionTool {

    protected abstract val lint: RsLint

    override fun isSuppressedFor(element: PsiElement): Boolean {
        if (super.isSuppressedFor(element)) return true
        return lint.levelFor(element) == RsLintLevel.ALLOW
    }

    override fun getSuppressActions(element: PsiElement?): Array<SuppressIntentionAction>? {
        return emptyArray()
    }
}
