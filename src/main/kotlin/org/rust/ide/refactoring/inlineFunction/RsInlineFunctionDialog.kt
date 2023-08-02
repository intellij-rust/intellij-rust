/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.inlineFunction

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiReference
import com.intellij.refactoring.RefactoringBundle
import org.rust.RsBundle
import org.rust.ide.refactoring.RsInlineDialog
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsUseItem
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.isMethod
import org.rust.lang.core.resolve.ref.RsReference

class RsInlineFunctionDialog(
    private val function: RsFunction,
    private val refElement: RsReference?,
    /** If true, we can't inline other usages */
    private val allowInlineThisOnly: Boolean,
    project: Project = function.project,
) : RsInlineDialog(function, refElement, project) {

    private val occurrencesNumber: Int = getNumberOfOccurrences(function)
    private val callableType: String = if (function.isMethod) "Method" else "Function"

    init {
        init()
    }

    override fun canInlineThisOnly(): Boolean = allowInlineThisOnly

    override fun allowInlineAll(): Boolean = true

    override fun ignoreOccurrence(reference: PsiReference): Boolean =
        reference.element.ancestorStrict<RsUseItem>() == null

    override fun doAction() {
        val inlineThisOnly = allowInlineThisOnly || isInlineThisOnly
        val removeDefinition = myRbInlineAll.isSelected && function.isWritable
        val processor = RsInlineFunctionProcessor(project, function, refElement, inlineThisOnly, removeDefinition)
        invokeRefactoring(processor)
    }

    override fun getBorderTitle(): String = RsBundle.message("border.title.inline", callableType)

    override fun getNameLabelText(): String =
        RsBundle.message("label.2", callableType, function.name?:"", getOccurrencesText(occurrencesNumber))

    override fun getInlineAllText(): String {
        val text = if (function.isWritable) {
            "all.invocations.and.remove.the.method"
        } else {
            "all.invocations.in.project"
        }
        return RefactoringBundle.message(text)
    }

    override fun getInlineThisText(): String =
        RefactoringBundle.message("this.invocation.only.and.keep.the.method")

    override fun getKeepTheDeclarationText(): String? {
        // `occurrencesNumber` can be negative if calculating it takes too long
        val mightHaveMultipleOccurrences = occurrencesNumber < 0 || occurrencesNumber > 1
        return if (function.isWritable && (mightHaveMultipleOccurrences || !myInvokedOnReference)) {
            RsBundle.message("radio.inline.all.keep.method")
        } else {
            null
        }
    }

    override fun getHelpId(): String =
        "refactoring.inlineMethod"
}
