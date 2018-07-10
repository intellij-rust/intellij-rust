/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.Messages
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import org.rust.lang.core.cfg.ControlFlowGraph
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.openapiext.execute
import org.rust.openapiext.isSuccess
import org.rust.openapiext.withWorkDirectory
import java.awt.Desktop
import java.io.File
import java.nio.file.Paths

class RsShowControlFlowGraphAction : AnAction() {
    private val tempDir = Paths.get(PathManager.getTempPath()).resolve(CONTROL_FLOW_GRAPH)

    init {
        tempDir.delete()
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = getBody(event.dataContext) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val body = getBody(event.dataContext) ?: return

        val cfg = ControlFlowGraph.buildFor(body)
        val dotDescription = cfg.createDotDescription()

        val dotFileName = "cfg${cfg.hashCode()}.dot"
        val pngFileName = "cfg${cfg.hashCode()}.png"

        val dotFile = ProgressManager.getInstance().runProcessWithProgressSynchronously<File, ExecutionException>({
            createTempFileWithContent(dotFileName, dotDescription)
        }, "Creating dot file...", true, event.project)

        val pngFile = tempDir.resolve(pngFileName).toFile()

        val result = GeneralCommandLine("dot", "-Tpng", dotFileName, "-o", pngFileName)
            .withWorkDirectory(tempDir)
            .execute()

        if (result != null && result.isSuccess && pngFile.exists()) {
            Desktop.getDesktop().open(pngFile)
        } else {
            Messages.showInfoMessage("Please ensure that you have Graphviz installed", "Cannot show graph")
        }

        dotFile.deleteOnExit()
        pngFile.deleteOnExit()
    }

    private fun createTempFileWithContent(fileName: String, content: String): File {
        tempDir.createDirectories()
        val file = tempDir.resolve(fileName).toFile()
        file.writeText(content)
        return file
    }

    private fun getBody(dataContext: DataContext): RsBlock? {
        val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return null
        val file = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return null
        val elementAtCaret = file.findElementAt(editor.caretModel.offset) ?: return null
        return elementAtCaret.ancestorOrSelf<RsFunction>()?.block
    }

    companion object {
        private const val CONTROL_FLOW_GRAPH = "control_flow_graph"
    }
}
