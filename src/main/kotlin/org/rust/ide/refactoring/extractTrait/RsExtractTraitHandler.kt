/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.extractTrait

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.TestOnly
import org.rust.RsBundle
import org.rust.ide.refactoring.RsMemberInfo
import org.rust.ide.refactoring.RsMemberSelectionPanel
import org.rust.ide.refactoring.isValidRustVariableIdentifier
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.RsItemElement
import org.rust.lang.core.psi.ext.RsTraitOrImpl
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.childrenOfType
import org.rust.openapiext.addTextChangeListener
import org.rust.openapiext.fullWidthCell
import org.rust.openapiext.isUnitTestMode
import javax.swing.JComponent
import javax.swing.JTextField

class RsExtractTraitHandler : RefactoringActionHandler {
    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return
        val traitOrImpl = element.ancestorOrSelf<RsTraitOrImpl>() ?: return
        if (traitOrImpl is RsImplItem && traitOrImpl.traitRef != null) return
        if (traitOrImpl is RsTraitItem && traitOrImpl.typeParameterList != null) return  // TODO
        if (!CommonRefactoringUtil.checkReadOnlyStatus(project, traitOrImpl)) return

        val members = (traitOrImpl.members ?: return).childrenOfType<RsItemElement>()
        if (members.isEmpty()) return
        val memberInfos = members.map { RsMemberInfo(it, false) }

        if (isUnitTestMode) {
            invokeInUnitTestMode(traitOrImpl)
        } else {
            val dialog = RsExtractTraitDialog(project, traitOrImpl, memberInfos)
            dialog.show()
        }
    }

    private fun invokeInUnitTestMode(traitOrImpl: RsTraitOrImpl) {
        val members = traitOrImpl.members
            ?.childrenOfType<RsItemElement>()
            .orEmpty()
            .filter { it.getUserData(RS_EXTRACT_TRAIT_MEMBER_IS_SELECTED) != null }
        val processor = RsExtractTraitProcessor(traitOrImpl, "Trait", members)
        processor.run()
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        /* not called from the editor */
    }
}

class RsExtractTraitDialog(
    project: Project,
    private val traitOrImpl: RsTraitOrImpl,
    private val memberInfos: List<RsMemberInfo>,
) : RefactoringDialog(project, false) {

    private val traitNameField: JTextField = JBTextField().apply {
        addTextChangeListener { validateButtons() }
    }

    init {
        check(!isUnitTestMode)
        super.init()
        title = RsBundle.message("action.Rust.RsExtractTrait.dialog.title")
        validateButtons()
    }

    override fun createCenterPanel(): JComponent = panel {
        row {
            label(RsBundle.message("label.trait.name"))
        }
        row {
            fullWidthCell(traitNameField).focused()
        }.bottomGap(BottomGap.MEDIUM)

        row {
            resizableRow()
            val members = RsMemberSelectionPanel(RsBundle.message("separator.members.to.form.trait"), memberInfos)
            members.minimumSize = JBUI.size(0, 200)
            members.table.addMemberInfoChangeListener { validateButtons() }
            fullWidthCell(members)
                .verticalAlign(VerticalAlign.FILL)
        }
    }

    override fun validateButtons() {
        super.validateButtons()
        previewAction.isEnabled = false
    }

    override fun areButtonsValid(): Boolean =
        isValidRustVariableIdentifier(traitNameField.text) && memberInfos.any { it.isChecked }

    override fun doAction() {
        try {
            CommandProcessor.getInstance().executeCommand(
                project,
                { doActionUndoCommand() },
                title,
                null
            )
        } catch (e: Exception) {
            logger<RsExtractTraitHandler>().error(e)
            project.showRefactoringError(e.message)
        }
    }

    private fun doActionUndoCommand() {
        val members = memberInfos.filter { it.isChecked }.map { it.member }
        val traitName = traitNameField.text
        val processor = RsExtractTraitProcessor(traitOrImpl, traitName, members)
        invokeRefactoring(processor)
    }
}

@TestOnly
val RS_EXTRACT_TRAIT_MEMBER_IS_SELECTED: Key<Boolean> = Key("RS_EXTRACT_TRAIT_MEMBER_IS_SELECTED")

private fun Project.showRefactoringError(@NlsContexts.DialogMessage message: String?, helpId: String? = null) {
    val title = RefactoringBundle.message("error.title")
    CommonRefactoringUtil.showErrorMessage(title, message, helpId, this)
}
