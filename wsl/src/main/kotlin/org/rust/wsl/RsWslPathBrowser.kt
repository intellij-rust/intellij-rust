package org.rust.wsl

import com.intellij.execution.wsl.WSLDistributionWithRoot
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.TextAccessor
import org.rust.openapiext.runUnderProgress
import java.io.File
import javax.swing.JComponent

class RsWslPathBrowser(private val field: TextAccessor) {

    fun browsePath(distro: WSLDistributionWithRoot, parent: JComponent) {
        val virtualFile = ProgressManager.getInstance().runUnderProgress("Opening WSL...") { getLocalPath(distro) }
        if (virtualFile == null) {
            JBPopupFactory.getInstance()
                .createMessage("Can't find the Windows part of this distribution, can't browse it")
                .show(parent)
            return
        }
        val dialog = FileChooserDialogImpl(FileChooserDescriptorFactory.createAllButJarContentsDescriptor(), parent)
        val files = dialog.choose(null, virtualFile)
        val path = files.firstOrNull()?.let { distro.getWslPath(it.path) } ?: return
        field.text = path
    }

    private fun getLocalPath(distro: WSLDistributionWithRoot): VirtualFile? {
        val fs = LocalFileSystem.getInstance()
        var file: VirtualFile? = null
        distro.getWindowsPath(field.text)?.let {
            var fileName = it
            while (file == null) {
                file = fs.findFileByPath(fileName)
                fileName = File(fileName).parent
            }
        }
        return file
    }
}
