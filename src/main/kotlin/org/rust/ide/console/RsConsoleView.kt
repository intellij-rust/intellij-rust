/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.execution.console.LanguageConsoleImpl
import com.intellij.execution.impl.ConsoleViewUtil
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ObservableConsoleView
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.IdeFocusManager
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsReplCodeFragment
import org.rust.openapiext.toPsiFile
import javax.swing.JComponent

class RsConsoleView(project: Project) : LanguageConsoleImpl(project, VIRTUAL_FILE_NAME, RsLanguage),
                                        ObservableConsoleView {

    lateinit var executeActionHandler: RsConsoleExecuteActionHandler

    private val initialized: ActionCallback = ActionCallback()
    val codeFragment: RsReplCodeFragment? = virtualFile.toPsiFile(project) as? RsReplCodeFragment
    private val codeFragmentContext: RsConsoleCodeFragmentContext = RsConsoleCodeFragmentContext()

    init {
        val virtualFile = virtualFile
        virtualFile.putUserData(RUST_CONSOLE_KEY, true)
        // Mark editor as console one, to prevent autopopup completion
        historyViewer.putUserData(ConsoleViewUtil.EDITOR_IS_CONSOLE_HISTORY_VIEW, true)
        super.setPrompt(PROMPT)
        setUpdateFoldingsEnabled(false)
    }

    override fun requestFocus() {
        initialized.doWhenDone {
            IdeFocusManager.getGlobalInstance().requestFocus(consoleEditor.contentComponent, true)
        }
    }

    override fun createCenterComponent(): JComponent {
        // workaround for extra lines appearing in the console
        val centerComponent = super.createCenterComponent()
        historyViewer.settings.additionalLinesCount = 0
        historyViewer.settings.isUseSoftWraps = false
        consoleEditor.gutterComponentEx.background = consoleEditor.backgroundColor
        consoleEditor.gutterComponentEx.revalidate()
        consoleEditor.colorsScheme.setColor(EditorColors.GUTTER_BACKGROUND, consoleEditor.backgroundColor)

        return centerComponent
    }

    fun print(text: String, attributes: Key<*>) {
        print(text, outputTypeForAttributes(attributes))
    }

    private fun outputTypeForAttributes(attributes: Key<*>): ConsoleViewContentType = when {
        attributes === ProcessOutputTypes.STDERR -> ConsoleViewContentType.ERROR_OUTPUT
        attributes === ProcessOutputTypes.SYSTEM -> ConsoleViewContentType.SYSTEM_OUTPUT
        else -> ConsoleViewContentType.getConsoleViewType(attributes)
    }

    fun initialized() = initialized.setDone()

    fun addToContext(lastCommandContext: RsConsoleOneCommandContext) {
        if (codeFragment != null) {
            codeFragmentContext.addToContext(lastCommandContext)
            codeFragmentContext.updateContext(project, codeFragment)
        }
    }

    companion object {
        const val PROMPT: String = ">> "
        const val VIRTUAL_FILE_NAME: String = "IntellijRustRepl.rs"
        private val RUST_CONSOLE_KEY: Key<Boolean> = Key("RS_CONSOLE_KEY")
    }
}
