/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.annotations.TestOnly
import org.rust.RsBundle
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.ide.notifications.showBalloon
import org.rust.lang.RsConstants.MOD_RS_FILE
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsModDeclItem
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.childrenOfType
import org.rust.lang.core.psi.ext.containingCargoPackage
import org.rust.lang.core.psi.ext.firstItem
import org.rust.lang.core.psi.rustFile
import org.rust.openapiext.*
import org.rust.stdext.exhaustive

/**
 * Attaches a file to a Rust module.
 *
 * Before fix:
 * foo.rs (not attached)
 * lib.rs
 *
 * After fix:
 * foo.rs
 * lib.rs
 *  mod foo;
 */
class AttachFileToModuleFix(
    file: RsFile,
    private val targetModuleName: String? = null
) : LocalQuickFixOnPsiElement(file) {
    override fun getFamilyName(): String = text
    override fun getText(): String = RsBundle.message("intention.name.attach.file.to", targetModuleName ?: "a module")

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        val rsFile = startElement as? RsFile ?: return
        val availableModules = findAvailableModulesForFile(project, rsFile)

        when (availableModules.size) {
            0 -> return
            1 -> insertFileToModule(rsFile, availableModules[0])
            else -> selectModule(rsFile, availableModules)?.let { insertFileToModule(rsFile, it) }
        }.exhaustive
    }

    override fun startInWriteAction(): Boolean {
        return false
    }

    private fun insertFileToModule(file: RsFile, targetFile: RsFile) {
        val project = file.project
        val factory = RsPsiFactory(project)

        // if the filename is mod.rs, attach its parent directory
        val name = if (file.isModuleFile) {
            file.virtualFile.parent.name
        } else {
            file.virtualFile.nameWithoutExtension
        }

        val modItem = factory.tryCreateModDeclItem(name)
        if (modItem == null) {
            project.showBalloon(RsBundle.message("notification.content.could.not.create.mod", name), NotificationType.ERROR)
            return
        }

        project.runWriteCommandAction(text, targetFile) {
            insertModItem(modItem, targetFile).navigate(true)
        }
    }

    companion object {

        fun createIfCompatible(project: Project, file: RsFile): AttachFileToModuleFix? {
            val availableModules = findAvailableModulesForFile(project, file)
            return if (availableModules.isNotEmpty()) {
                val moduleLabel = if (availableModules.size == 1) {
                    availableModules[0].name
                } else {
                    null
                }
                AttachFileToModuleFix(file, moduleLabel)
            } else {
                null
            }
        }

        private fun findAvailableModulesForFile(project: Project, file: RsFile): List<RsFile> {
            val virtualFile = file.virtualFile ?: return emptyList()
            val pkg = project.cargoProjects.findPackageForFile(virtualFile) ?: return emptyList()
            val directory = virtualFile.parent ?: return emptyList()

            val modules = mutableListOf<RsFile>()

            if (file.isModuleFile) {
                // module file in parent directory
                modules.addIfNotNull(findModule(file, project, directory.parent?.findFileByRelativePath(MOD_RS_FILE)))

                // package target roots in parent directory
                for (target in pkg.targets) {
                    val crateRoot = target.crateRoot ?: continue
                    if (crateRoot.parent == directory.parent) {
                        modules.addIfNotNull(crateRoot.toPsiFile(project)?.rustFile)
                    }
                }
            } else {
                // mod.rs in the same directory
                modules.addIfNotNull(findModule(file, project, directory.findFileByRelativePath(MOD_RS_FILE)))

                // module file in parent directory
                if (pkg.edition >= CargoWorkspace.Edition.EDITION_2018) {
                    modules.addIfNotNull(findModule(file, project, directory.parent?.findFileByRelativePath("${directory.name}.rs")))
                }

                // package target roots in the same directory
                for (target in pkg.targets) {
                    val crateRoot = target.crateRoot ?: continue
                    if (crateRoot.parent == directory) {
                        modules.addIfNotNull(crateRoot.toPsiFile(project)?.rustFile)
                    }
                }
            }

            return modules
        }
    }
}

private fun selectModule(file: RsFile, availableModules: List<RsFile>): RsFile? {
    if (isUnitTestMode) {
        val mock = MOCK ?: error("You should set mock module selector via withMockModuleAttachSelector")
        return mock(file, availableModules)
    }

    val box = ComboBox<RsFile>()
    with(box) {
        for (module in availableModules) {
            addItem(module)
        }
        renderer = SimpleListCellRenderer.create("") {
            val root = it.containingCargoPackage?.rootDirectory
            val path = it.containingFile.virtualFile.pathAsPath
            (root?.relativize(path) ?: path).toString()
        }
    }

    val dialog = dialog(RsBundle.message("dialog.title.select.module"), panel {
        row { fullWidthCell(box) }
    }, focusedComponent = box)

    return if (dialog.showAndGet()) {
        box.selectedItem as? RsFile
    } else {
        null
    }
}

private fun findModule(root: RsFile, project: Project, file: VirtualFile?): RsFile? {
    if (file == null) return null
    val module = file.toPsiFile(project)?.rustFile ?: return null
    if (module == root || module.crateRoot == null) return null
    return module
}

private fun insertModItem(item: RsModDeclItem, module: RsFile): RsModDeclItem {
    val anchor = module.firstItem
    val existingMod = module.childrenOfType<RsModDeclItem>().lastOrNull()

    val inserted = when {
        existingMod != null -> module.addAfter(item, existingMod)
        anchor != null -> module.addBefore(item, anchor)
        else -> module.add(item)
    }
    return inserted as RsModDeclItem
}

private val RsFile.isModuleFile
    get() = name == MOD_RS_FILE

typealias ModuleAttachSelector = (file: RsFile, availableModules: List<RsFile>) -> RsFile?

private var MOCK: ModuleAttachSelector? = null

@TestOnly
fun withMockModuleAttachSelector(
    mock: ModuleAttachSelector,
    f: () -> Unit
) {
    MOCK = mock
    try {
        f()
    } finally {
        MOCK = null
    }
}
