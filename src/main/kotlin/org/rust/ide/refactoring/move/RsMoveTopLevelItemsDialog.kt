/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapiext.isUnitTestMode
import com.intellij.refactoring.RefactoringBundle.message
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.panel
import com.intellij.util.IncorrectOperationException
import org.rust.lang.core.psi.ext.RsItemElement
import org.rust.lang.core.psi.ext.RsMod
import org.rust.openapiext.pathToRsFileTextField
import org.rust.openapiext.toPsiFile
import org.rust.stdext.mapToSet
import java.awt.Dimension
import java.io.File
import javax.swing.JComponent

class RsMoveTopLevelItemsDialog(
    project: Project,
    private val itemsToMove: Set<RsItemElement>,
    private val sourceMod: RsMod
) : RefactoringDialog(project, false) {

    private val sourceFilePath: String = sourceMod.containingFile.virtualFile.path
    private val sourceFileField: JBTextField = JBTextField(sourceFilePath).apply { isEnabled = false }
    private val targetFileChooser: TextFieldWithBrowseButton = createTargetFileChooser(project)
    private val memberPanel: RsMemberSelectionPanel = createMemberInfoPanel()

    private var searchForReferences: Boolean = true

    init {
        super.init()
        title = "Move Module Items"
    }

    private fun createTargetFileChooser(project: Project): TextFieldWithBrowseButton {
        return pathToRsFileTextField(disposable, "Choose Destination File", project)
            .also {
                it.text = sourceFilePath
                it.textField.caretPosition = sourceFilePath.removeSuffix(".rs").length
                it.textField.moveCaretPosition(sourceFilePath.lastIndexOf('/') + 1)
            }
    }

    private fun createMemberInfoPanel(): RsMemberSelectionPanel {
        val memberInfo = getTopLevelItems()
            .map { RsMemberInfo(it, itemsToMove.contains(it)) }
        return RsMemberSelectionPanel("Items to move", memberInfo)
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row("From:") {
                sourceFileField(growX).withLargeLeftGap()
            }
            row("To:") {
                targetFileChooser(growX).withLargeLeftGap().focused()
            }
            row {
                memberPanel(grow, pushY)
            }
            row {
                cell(isFullWidth = true) {
                    checkBox(message("search.for.references"), searchForReferences)
                }
            }
        }.also { it.preferredSize = Dimension(600, 400) }
    }

    private fun getTopLevelItems(): List<RsItemElement> {
        return sourceMod.children
            .filterIsInstance<RsItemElement>()
            .filter { RsMoveTopLevelItemsHandler.canMoveElement(it) }
    }

    public override fun doAction() {
        val itemsToMove = memberPanel.table.selectedMemberInfos.mapToSet { it.member }
        val targetFilePath = targetFileChooser.text

        val targetMod = findTargetMod(targetFilePath)
        if (targetMod == null) {
            val message = "Target file must be a Rust file"
            CommonRefactoringUtil.showErrorMessage(message("error.title"), message, null, project)
            return
        }

        try {
            val processor = RsMoveTopLevelItemsProcessor(project, itemsToMove, targetMod, searchForReferences)
            invokeRefactoring(processor)
        } catch (e: IncorrectOperationException) {
            if (isUnitTestMode) throw e
            CommonRefactoringUtil.showErrorMessage(message("error.title"), e.message, null, project)
        }
    }

    private fun findTargetMod(targetFilePath: String): RsMod? {
        if (isUnitTestMode) return sourceMod.containingFile.getUserData(MOVE_TARGET_MOD_KEY)
        val targetFile = LocalFileSystem.getInstance().findFileByIoFile(File(targetFilePath))
        return targetFile?.toPsiFile(project) as? RsMod
    }
}

val MOVE_TARGET_MOD_KEY: Key<RsMod> = Key("RS_MOVE_TARGET_MOD_KEY")
