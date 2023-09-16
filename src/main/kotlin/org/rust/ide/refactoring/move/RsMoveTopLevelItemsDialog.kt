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
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.util.IncorrectOperationException
import com.intellij.util.ui.JBUI
import org.apache.commons.lang.StringEscapeUtils
import org.jetbrains.annotations.Nls
import org.rust.RsBundle
import org.rust.ide.docs.signature
import org.rust.lang.RsConstants
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsItemElement
import org.rust.lang.core.psi.ext.RsMod
import org.rust.openapiext.*
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

    @Nls
    private val sourceFilePath: String = sourceMod.containingFile.virtualFile.path
    private val sourceFileField: JBTextField = JBTextField(sourceFilePath).apply { isEnabled = false }
    private val targetFileChooser: TextFieldWithBrowseButton = createTargetFileChooser(project)
    private val memberPanel: RsMoveMemberSelectionPanel = createMemberSelectionPanel().apply {
        // Small hack to make Kotlin UI DSL 2 use proper minimal size
        // Actually, I don't know why it helps
        preferredSize = JBUI.size(0, 0)
    }

    private var searchForReferences: Boolean = true

    init {
        check(!isUnitTestMode)
        super.init()
        title = RsBundle.message("dialog.title.move.module.items")
        validateButtons()
    }

    private fun createTargetFileChooser(project: Project): TextFieldWithBrowseButton {
        return pathToRsFileTextField(disposable, RsBundle.message("dialog.title.choose.destination.file"), project, ::validateButtons)
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

        return RsMoveMemberSelectionPanel(project, RsBundle.message("separator.items.to.move"), nodesAll, nodesSelected)
            .also { it.tree.setInclusionListener { validateButtons() } }
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row(RsBundle.message("from")) {
                fullWidthCell(sourceFileField)
            }
            row(RsBundle.message("to")) {
                fullWidthCell(targetFileChooser).focused()
            }
            row {
                resizableRow()
                fullWidthCell(memberPanel)
                    .verticalAlign(VerticalAlign.FILL)
            }
            row {
                checkBox(RefactoringBundle.message("search.for.references"))
                    .bindSelected(::searchForReferences)
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

    override fun doAction() {
        // we want that file creation is undo together with actual move
        CommandProcessor.getInstance().executeCommand(
            project,
            { doActionUndoCommand() },
            RefactoringBundle.message("move.title"),
            null
        )
    }

    private fun doActionUndoCommand() {
        val itemsToMove = getSelectedItems()
        val targetFilePath = targetFileChooser.text.toPath()
        val targetMod = getOrCreateTargetMod(targetFilePath, project, sourceMod.crateRoot) ?: return
        try {
            val processor = RsMoveTopLevelItemsProcessor(project, itemsToMove, targetMod, searchForReferences)
            invokeRefactoring(processor)
        } catch (e: Exception) {
            if (e !is IncorrectOperationException) {
                Logger.getInstance(RsMoveTopLevelItemsDialog::class.java).error(e)
            }
            project.showErrorMessage(e.message)
        }
    }

    companion object {
        fun getOrCreateTargetMod(targetFilePath: Path, project: Project, crateRoot: RsMod?): RsMod? {
            val targetFile = LocalFileSystem.getInstance().findFileByNioFile(targetFilePath)
            return if (targetFile != null) {
                targetFile.toPsiFile(project) as? RsMod
                    ?: run {
                        project.showErrorMessage(RsBundle.message("dialog.message.target.file.must.be.rust.file"))
                        null
                    }
            } else {
                try {
                    createNewRustFile(targetFilePath, project, crateRoot, this)
                        ?: run {
                            project.showErrorMessage(RsBundle.message("dialog.message.can.t.create.new.rust.file.or.attach.it.to.module.tree"))
                            null
                        }
                } catch (e: Exception) {
                    project.showErrorMessage(RsBundle.message("dialog.message.error.during.creating.new.rust.file", e.message?:""))
                    null
                }
            }
        }

        private fun Project.showErrorMessage(@DialogMessage message: String?) {
            val title = RefactoringBundle.message("error.title")
            CommonRefactoringUtil.showErrorMessage(title, message, null, this)
        }
    }
}

class RsMoveMemberInfo(val member: RsItemElement) : RsMoveNodeInfo {
    override fun render(renderer: ColoredTreeCellRenderer) {
        val description = if (member is RsModItem) {
            RsBundle.message("mod.0", member.modName?:"")
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
            is RsStructItem -> RsBundle.message("struct")
            is RsEnumItem -> RsBundle.message("enum")
            is RsTypeAlias -> RsBundle.message("type2")
            is RsTraitItem -> RsBundle.message("trait")
            else -> null
        }
        if (name == null || keyword == null) {
            renderer.append(RsBundle.message("item.and.impls"), SimpleTextAttributes.REGULAR_ATTRIBUTES)
            return
        }

        // "$keyword $name and impls"
        renderer.append("$keyword ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
        renderer.append(name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        renderer.append(RsBundle.message("and.impls"), SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }

    override val children: List<RsMoveMemberInfo> =
        listOf(RsMoveMemberInfo(item)) + impls.map { RsMoveMemberInfo(it) }
}

val MOVE_TARGET_MOD_KEY: Key<RsMod> = Key("RS_MOVE_TARGET_MOD_KEY")
val MOVE_TARGET_FILE_PATH_KEY: Key<Path> = Key("RS_MOVE_TARGET_FILE_PATH_KEY")

/** Creates new rust file and attaches it to parent mod */
private fun createNewRustFile(filePath: Path, project: Project, crateRoot: RsMod?, requestor: Any?): RsFile? {
    return project.runWriteCommandAction(RefactoringBundle.message("move.title")) {
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
    if (file.isCrateRoot) return true
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
