/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.refactoring.RefactoringBundle.message
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.panel
import com.intellij.util.IncorrectOperationException
import org.apache.commons.lang.StringEscapeUtils
import org.rust.ide.docs.signature
import org.rust.lang.RsConstants
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsItemElement
import org.rust.lang.core.psi.ext.RsMod
import org.rust.openapiext.isUnitTestMode
import org.rust.openapiext.pathToRsFileTextField
import org.rust.openapiext.runWriteCommandAction
import org.rust.openapiext.toPsiFile
import org.rust.stdext.mapToSet
import org.rust.stdext.toPath
import java.awt.Dimension
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JComponent

class RsMoveTopLevelItemsDialog(
    project: Project,
    private val itemsToMove: Set<RsItemElement>,
    private val sourceMod: RsMod
) : RefactoringDialog(project, false) {

    private val sourceFilePath: String = sourceMod.containingFile.virtualFile.path
    private val sourceFileField: JBTextField = JBTextField(sourceFilePath).apply { isEnabled = false }
    private val targetFileChooser: TextFieldWithBrowseButton = createTargetFileChooser(project)
    private val memberPanel: RsMoveMemberSelectionPanel = createMemberSelectionPanel()

    private var searchForReferences: Boolean = true

    init {
        super.init()
        title = "Move Module Items"
        validateButtons()
    }

    private fun createTargetFileChooser(project: Project): TextFieldWithBrowseButton {
        return pathToRsFileTextField(disposable, "Choose Destination File", project, ::validateButtons)
            .also {
                it.text = sourceFilePath
                it.textField.caretPosition = sourceFilePath.removeSuffix(".rs").length
                it.textField.moveCaretPosition(sourceFilePath.lastIndexOf('/') + 1)
            }
    }

    private fun createMemberSelectionPanel(): RsMoveMemberSelectionPanel {
        val topLevelItems = getTopLevelItems()

        val nodesGroupedWithImpls = groupImplsByStructOrTrait(sourceMod, topLevelItems.toSet())
            .map { RsMoveItemAndImplsInfo(it.key, it.value) }
        val itemsGroupedWithImpls = nodesGroupedWithImpls.flatMap { it.children }.map { it.member }

        val nodesWithoutGrouping = topLevelItems.subtract(itemsGroupedWithImpls).map { RsMoveMemberInfo(it) }
        val nodesAll = nodesGroupedWithImpls + nodesWithoutGrouping

        val nodesSelected = nodesAll
            .flatMap {
                when (it) {
                    is RsMoveItemAndImplsInfo -> it.children
                    is RsMoveMemberInfo -> listOf(it)
                    else -> error("unexpected node info type: $it")
                }
            }
            .filter { it.member in itemsToMove }

        return RsMoveMemberSelectionPanel(project, "Items to move", nodesAll, nodesSelected)
            .also { it.tree.setInclusionListener { validateButtons() } }
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
                    checkBox(message("search.for.references"), ::searchForReferences)
                }
            }
        }.also { it.preferredSize = Dimension(600, 400) }
    }

    private fun getTopLevelItems(): List<RsItemElement> {
        return sourceMod.children
            .filterIsInstance<RsItemElement>()
            .filter { RsMoveTopLevelItemsHandler.canMoveElement(it) }
    }

    override fun areButtonsValid(): Boolean {
        // `memberPanel` is initialized after `createTargetFileChooser`,
        // which triggers `areButtonsValid` check
        @Suppress("SENSELESS_COMPARISON")
        if (memberPanel == null) return false

        return sourceFilePath != targetFileChooser.text && getSelectedItems().isNotEmpty()
    }

    private fun getSelectedItems(): Set<RsItemElement> =
        memberPanel.tree.includedSet
            .filterIsInstance<RsMoveMemberInfo>()
            .mapToSet { it.member }

    public override fun doAction() {
        // we want that file creation is undo together with actual move
        CommandProcessor.getInstance().executeCommand(
            project,
            { doActionUndoCommand() },
            message("move.title"),
            null
        )
    }

    private fun doActionUndoCommand() {
        val itemsToMove = getSelectedItems()
        val targetFilePath = targetFileChooser.text.toPath()
        val targetMod = getOrCreateTargetMod(targetFilePath) ?: return
        try {
            val processor = RsMoveTopLevelItemsProcessor(project, itemsToMove, targetMod, searchForReferences)
            invokeRefactoring(processor)
        } catch (e: Exception) {
            if (isUnitTestMode) throw e
            if (e !is IncorrectOperationException) {
                Logger.getInstance(RsMoveTopLevelItemsDialog::class.java).error(e)
            }
            showErrorMessage(e.message)
        }
    }

    private fun getOrCreateTargetMod(targetFilePath: Path): RsMod? {
        if (isUnitTestMode) {
            val sourceFile = sourceMod.containingFile
            return sourceFile.getUserData(MOVE_TARGET_MOD_KEY)
                ?: doGetOrCreateTargetMod(sourceFile.getUserData(MOVE_TARGET_FILE_PATH_KEY)!!)!!
        }
        return doGetOrCreateTargetMod(targetFilePath)
    }

    private fun doGetOrCreateTargetMod(targetFilePath: Path): RsMod? {
        val targetFile = LocalFileSystem.getInstance().findFileByNioFile(targetFilePath)
        return if (targetFile != null) {
            targetFile.toPsiFile(project) as? RsMod
                ?: run {
                    showErrorMessage("Target file must be a Rust file")
                    null
                }
        } else {
            try {
                createNewRustFile(targetFilePath, project, sourceMod.crateRoot, this)
                    ?: run {
                        showErrorMessage("Can't create new Rust file or attach it to module tree")
                        null
                    }
            } catch (e: Exception) {
                showErrorMessage("Error during creating new Rust file: ${e.message}")
                null
            }
        }
    }

    private fun showErrorMessage(@Suppress("UnstableApiUsage") @DialogMessage message: String?) {
        val title = message("error.title")
        CommonRefactoringUtil.showErrorMessage(title, message, null, project)
    }
}

class RsMoveMemberInfo(val member: RsItemElement) : RsMoveNodeInfo {
    override fun render(renderer: ColoredTreeCellRenderer) {
        val description = if (member is RsModItem) {
            "mod ${member.modName}"
        } else {
            val descriptionHTML = buildString { member.signature(this) }
            val description = StringEscapeUtils.unescapeHtml(StringUtil.removeHtmlTags(descriptionHTML))
            description.replace("(?U)\\s+".toRegex(), " ")
        }
        renderer.append(description, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }

    override val icon: Icon = member.getIcon(0)
}

class RsMoveItemAndImplsInfo(
    private val item: RsItemElement, // struct or trait
    impls: List<RsImplItem>
) : RsMoveNodeInfo {

    @Suppress("DialogTitleCapitalization")
    override fun render(renderer: ColoredTreeCellRenderer) {
        val name = item.name
        val keyword = when (item) {
            is RsStructItem -> "struct"
            is RsEnumItem -> "enum"
            is RsTypeAlias -> "type"
            is RsTraitItem -> "trait"
            else -> null
        }
        if (name == null || keyword == null) {
            renderer.append("item and impls", SimpleTextAttributes.REGULAR_ATTRIBUTES)
            return
        }

        // "$keyword $name and impls"
        renderer.append("$keyword ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
        renderer.append(name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        renderer.append(" and impls", SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }

    override val children: List<RsMoveMemberInfo> =
        listOf(RsMoveMemberInfo(item)) + impls.map { RsMoveMemberInfo(it) }
}

val MOVE_TARGET_MOD_KEY: Key<RsMod> = Key("RS_MOVE_TARGET_MOD_KEY")
val MOVE_TARGET_FILE_PATH_KEY: Key<Path> = Key("RS_MOVE_TARGET_FILE_PATH_KEY")

/** Creates new rust file and attaches it to parent mod */
private fun createNewRustFile(filePath: Path, project: Project, crateRoot: RsMod?, requestor: Any?): RsFile? {
    return project.runWriteCommandAction {
        val fileSystem = (crateRoot as? RsFile)?.virtualFile?.fileSystem ?: LocalFileSystem.getInstance()
        createNewFile(filePath, fileSystem, requestor) { virtualFile ->
            val file = virtualFile.toPsiFile(project)?.rustFile ?: return@createNewFile null
            if (!attachFileToParentMod(file, project, crateRoot)) return@createNewFile null
            file
        }
    }
}

/** Finds parent mod of [file] and adds mod declaration to it */
private fun attachFileToParentMod(file: RsFile, project: Project, crateRoot: RsMod?): Boolean {
    val (parentModOwningDirectory, modName) = if (file.name == RsConstants.MOD_RS_FILE) {
        file.parent?.parent to file.parent?.name
    } else {
        file.parent to FileUtil.getNameWithoutExtension(file.name)
    }
    val parentMod = parentModOwningDirectory?.getOwningMod(crateRoot)
    if (parentMod == null || modName == null) return false
    val psiFactory = RsPsiFactory(project)
    parentMod.insertModDecl(psiFactory, psiFactory.createModDeclItem(modName))
    return true
}

/**
 * Creates new file (along with parent directories).
 * Then computes [action] on created [VirtualFile].
 * If [action] returns `null`, then rollbacks any changes, that is deletes created file and directories.
 */
private fun <T> createNewFile(
    filePath: Path,
    // needed for correct work in tests
    fileSystem: VirtualFileSystem,
    requestor: Any?,
    action: (VirtualFile) -> T?
): T? {
    val directoryPath = filePath.parent
    val directoriesToCreate = generateSequence(directoryPath) { it.parent }
        .takeWhile { fileSystem.findFileByPath(it.toString()) == null }
        .toList()

    val parentDirectory = VfsUtil.createDirectoryIfMissing(fileSystem, directoryPath.toString()) ?: return null
    val file = parentDirectory.createChildData(requestor, filePath.fileName.toString())
    action(file)?.let { return it }

    // else we need to delete created file and directories
    file.delete(null)
    for (directory in directoriesToCreate) {
        fileSystem.findFileByPath(directory.toString())?.delete(requestor)
    }
    return null
}
