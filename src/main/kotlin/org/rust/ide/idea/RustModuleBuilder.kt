package org.rust.ide.idea

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleBuilderListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModel
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.NotNull
import java.util.*

class RustModuleBuilder : ModuleBuilder(), ModuleBuilderListener {
    companion object Data {
        var project : Project? = null
        var bundle : ResourceBundle? = null
        @JvmStatic fun findContentEntry(@NotNull model : ModuleRootModel?, @NotNull file : VirtualFile?) : ContentEntry? {
            val entries : Array<ContentEntry?> = model?.contentEntries!!
            for (entry : ContentEntry? in entries) {
                val entryFile : VirtualFile? = entry?.file
                if (entryFile != null && VfsUtilCore.isAncestor(entryFile!!, file!!, false)) {
                    return entry
                }
            }
            return null
        }
    }
    override fun moduleCreated(p0: Module){
        val roots : Array<VirtualFile?> = ModuleRootManager.getInstance(p0).sourceRoots
        if(roots.size == 1){
            val sourceRoot = roots[0]
                val main = sourceRoot?.parent
                if (main != null && "main".equals(main.name)) {
                    val src : VirtualFile? = main.parent
                    if (src != null) {
                        ApplicationManager.getApplication().runWriteAction(RunWriteActionRunnable(this, src, p0))
                    }
                }
            }
        }
    override fun setupRootModel(modifiableRootModel: ModifiableRootModel?) {
    }

    override fun getModuleType(): ModuleType<*>? = RustModuleType.INSTANCE


}
