package org.rust.ide.refactoring.inline

import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.inline.InlineOptionsDialog
import com.intellij.refactoring.inline.InlineOptionsWithSearchSettingsDialog
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.RsNameIdentifierOwner
import org.rust.lang.core.psi.ext.declaration
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.lang.core.resolve.ref.RsReference

class RsInlineMethodDialog constructor(
    private val function: RsFunction,
    private val refElement: RsReference?,
    private val allowInlineThisOnly: Boolean,
    project: Project = function.project,
    private val occurrencesNumber: Int =
        initOccurrencesNumber(function)
) : InlineOptionsWithSearchSettingsDialog(project, true, function) {

    public override fun doAction() {
        invokeRefactoring(RsInlineMethodProcessor(
            project,
            function,
            refElement,
            isInlineThisOnly,
            !isInlineThisOnly && !isKeepTheDeclaration
        ))
    }

    fun shouldBeShown() = EditorSettingsExternalizable.getInstance().isShowInlineLocalDialog

    override fun getBorderTitle(): String =
        RefactoringBundle.message("inline.method.border.title")

    override fun getNameLabelText(): String {
        val occurrencesString =
            if (occurrencesNumber < 0) ""
            else
                buildString {
                    append("has $occurrencesNumber occurrence")
                    if (occurrencesNumber != 1) append("s")
                }
        return RefactoringBundle.message(
            "inline.method.method.label",
            function.declaration,
            occurrencesString)
    }

    override fun getInlineAllText(): String {
        val text = if (function.isWritable && !allowInlineThisOnly) "all.invocations.and.remove.the.method"
                    else "all.invocations.in.project"
        return RefactoringBundle.message(text)
    }

    override fun isInlineThis(): Boolean = false

    override fun getInlineThisText(): String =
        RefactoringBundle.message("this.invocation.only.and.keep.the.method")

    override fun getKeepTheDeclarationText(): String =
        RefactoringBundle.message("all.invocations.keep.the.method")

    override fun getHelpId(): String =
        "refactoring.inlineMethod"


    private var searchInCommentsAndStrings = true
    private var searchInTextOccurrences = true

    override fun isSearchInCommentsAndStrings() =
        searchInCommentsAndStrings

    override fun saveSearchInCommentsAndStrings(searchInComments: Boolean) {
        this.searchInCommentsAndStrings = searchInCommentsAndStrings
    }

    override fun isSearchForTextOccurrences(): Boolean =
        searchInTextOccurrences

    override fun saveSearchInTextOccurrences(searchInTextOccurrences: Boolean) {
        this.searchInTextOccurrences = searchInTextOccurrences
    }

    init {
        title = borderTitle
        myInvokedOnReference = refElement != null

        setPreviewResults(true)
        setDoNotAskOption(object : DialogWrapper.DoNotAskOption {
            override fun isToBeShown() = EditorSettingsExternalizable.getInstance().isShowInlineLocalDialog

            override fun setToBeShown(value: Boolean, exitCode: Int) {
                EditorSettingsExternalizable.getInstance().isShowInlineLocalDialog = value
            }

            override fun canBeHidden() = true

            override fun shouldSaveOptionsOnCancel() = false

            override fun getDoNotShowMessage() = "Do not show in future"
        })
        init()
    }
}
