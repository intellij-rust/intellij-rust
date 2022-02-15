/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.inlineFunction

import com.intellij.openapi.project.Project
import com.intellij.refactoring.RefactoringBundle
import org.rust.ide.refactoring.RsInlineDialog
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.declaration
import org.rust.lang.core.resolve.ref.RsReference

class RsInlineFunctionDialog(
    private val function: RsFunction,
    private val refElement: RsReference?,
    /** If true, we can't inline other usages */
    private val allowInlineThisOnly: Boolean,
    project: Project = function.project,
    private val occurrencesNumber: Int = initOccurrencesNumber(function)
) : RsInlineDialog(function, refElement, project, occurrencesNumber) {
    init {
        init()
    }

    override fun canInlineThisOnly(): Boolean = allowInlineThisOnly

    override fun allowInlineAll(): Boolean = true

    public override fun doAction() {
        val inlineThisOnly = allowInlineThisOnly || isInlineThisOnly
        val removeDefinition = myRbInlineAll.isSelected && function.isWritable
        val processor = RsInlineFunctionProcessor(project, function, refElement, inlineThisOnly, removeDefinition)
        invokeRefactoring(processor)
    }

    override fun getBorderTitle(): String =
        RefactoringBundle.message("inline.method.border.title")

    override fun getLabelText(occurrences: String): String {
        return RefactoringBundle.message(
            "inline.method.method.label",
            function.declaration,
            occurrences
        )
    }

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

    override fun getKeepTheDeclarationText(): String? =
        if (function.isWritable && (occurrencesNumber > 1 || !myInvokedOnReference)) {
            "Inline all and keep the method"
        } else {
            null
        }

    override fun getHelpId(): String =
        "refactoring.inlineMethod"
}
