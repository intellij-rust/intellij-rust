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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.panel
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.TestOnly
import org.rust.RsBundle
import org.rust.ide.refactoring.RsMemberInfo
import org.rust.ide.refactoring.RsMemberSelectionPanel
import org.rust.ide.refactoring.isValidRustVariableIdentifier
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.ext.RsItemElement
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.childrenOfType
import org.rust.openapiext.addTextChangeListener
import org.rust.openapiext.isUnitTestMode
import javax.swing.JComponent
import javax.swing.JTextField

class RsExtractTraitHandler : RefactoringActionHandler {
    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return
        val impl = element.ancestorOrSelf<RsImplItem>() ?: return
        if (impl.traitRef != null) return
        if (!CommonRefactoringUtil.checkReadOnlyStatus(project, impl)) return

        val members = (impl.members ?: return).childrenOfType<RsItemElement>()
        if (members.isEmpty()) return
        val memberInfos = members.map { RsMemberInfo(it, false) }

        val dialog = RsExtractTraitDialog(project, impl, memberInfos)
        if (isUnitTestMode) {
            dialog.doAction()
        } else {
            dialog.show()
        }
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        /* not called from the editor */
    }
}

class RsExtractTraitDialog(
    project: Project,
    private val impl: RsImplItem,
    private val memberInfos: List<RsMemberInfo>,
) : RefactoringDialog(project, false) {

    private val traitNameField: JTextField = JBTextField().apply {
        addTextChangeListener { validateButtons() }
    }

    init {
        super.init()
        title = RsBundle.message("action.Rust.RsExtractTrait.dialog.title")
        validateButtons()
    }

    override fun createCenterPanel(): JComponent = panel {
        blockRow {
            cell(isFullWidth = true) {
                label("Trait name:")
            }
            traitNameField().focused()
        }
        row {
            val members = RsMemberSelectionPanel("Members to form trait", memberInfos)
            members.minimumSize = JBUI.size(0, 200)
            members.table.addMemberInfoChangeListener { validateButtons() }
            members()
        }
    }

    override fun validateButtons() {
        super.validateButtons()
        previewAction.isEnabled = false
    }

    override fun areButtonsValid(): Boolean =
        isValidRustVariableIdentifier(traitNameField.text) && memberInfos.any { it.isChecked }

    public override fun doAction() {
        try {
            CommandProcessor.getInstance().executeCommand(
                project,
                { doActionUndoCommand() },
                title,
                null
            )
        } catch (e: Exception) {
            if (isUnitTestMode) throw e
            logger<RsExtractTraitHandler>().error(e)
            project.showRefactoringError(e.message)
        }
    }

    private fun doActionUndoCommand() {
        val (traitName, members) = getTraitNameAndSelectedMembers()
        val processor = RsExtractTraitProcessor(impl, traitName, members)
        invokeRefactoring(processor)
    }

    private fun getTraitNameAndSelectedMembers(): Pair<String, List<RsItemElement>> {
        return if (isUnitTestMode) {
            val members = impl.members
                ?.childrenOfType<RsItemElement>()
                .orEmpty()
                .filter { it.getUserData(RS_EXTRACT_TRAIT_MEMBER_IS_SELECTED) != null }
            "Trait" to members
        } else {
            val members = memberInfos.filter { it.isChecked }.map { it.member }
            traitNameField.text to members
        }
    }
}

@TestOnly
val RS_EXTRACT_TRAIT_MEMBER_IS_SELECTED: Key<Boolean> = Key("RS_EXTRACT_TRAIT_MEMBER_IS_SELECTED")

private fun Project.showRefactoringError(message: String?, helpId: String? = null) {
    val title = RefactoringBundle.message("error.title")
    CommonRefactoringUtil.showErrorMessage(title, message, helpId, this)
}
