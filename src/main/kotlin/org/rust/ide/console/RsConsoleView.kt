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
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.JBSplitter
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsReplCodeFragment
import org.rust.openapiext.toPsiFile
import java.awt.BorderLayout
import javax.swing.JComponent

class RsConsoleView(project: Project) : LanguageConsoleImpl(project, VIRTUAL_FILE_NAME, RsLanguage),
                                        ObservableConsoleView {

    lateinit var executeActionHandler: RsConsoleExecuteActionHandler

    private val initialized: ActionCallback = ActionCallback()
    val codeFragment: RsReplCodeFragment? = virtualFile.toPsiFile(project) as? RsReplCodeFragment
    private val codeFragmentContext: RsConsoleCodeFragmentContext = RsConsoleCodeFragmentContext(codeFragment)
    private var variablesView: RsConsoleVariablesView? = null
    private val options: RsConsoleOptions = RsConsoleOptions.getInstance(project)

    init {
        val virtualFile = virtualFile
        virtualFile.putUserData(RUST_CONSOLE_KEY, true)
        // Mark editor as console one, to prevent autopopup completion
        historyViewer.putUserData(ConsoleViewUtil.EDITOR_IS_CONSOLE_HISTORY_VIEW, true)
        super.setPrompt(PROMPT)
        consolePromptDecorator.indentPrompt = INDENT_PROMPT
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
            codeFragmentContext.updateContextAsync(project, codeFragment)
            variablesView?.rebuild()
        }
    }

    fun removeBorders() {
        historyViewer.setBorder(null)
        consoleEditor.setBorder(null)
    }

    val isShowVariables: Boolean
        get() = options.showVariables

    fun updateVariables(state: Boolean) {
        if (options.showVariables == state) return
        options.showVariables = state
        if (state) {
            showVariables()
        } else {
            hideVariables()
        }
    }

    private fun showVariables() {
        variablesView = RsConsoleVariablesView(project, codeFragmentContext)

        val console = getComponent(0)
        removeAll()
        val splitter = JBSplitter(false, 2f / 3).apply {
            firstComponent = console as JComponent
            secondComponent = variablesView
            isShowDividerControls = true
            setHonorComponentsMinimumSize(true)
        }
        add(splitter, BorderLayout.CENTER)
        validate()
        repaint()
    }

    private fun hideVariables() {
        val splitter = getComponent(0)
        val variablesView = variablesView
        if (variablesView != null && splitter is JBSplitter) {
            removeAll()
            Disposer.dispose(variablesView)
            this.variablesView = null
            add(splitter.firstComponent, BorderLayout.CENTER)
            validate()
            repaint()
        }
    }

    fun initVariablesWindow() {
        if (options.showVariables) {
            showVariables()
        }
    }

    override fun dispose() {
        super.dispose()
        variablesView?.let { Disposer.dispose(it) }
        variablesView = null
    }

    companion object {
        const val PROMPT: String = ">> "
        const val INDENT_PROMPT: String = ".. "
        const val VIRTUAL_FILE_NAME: String = "IntellijRustRepl.rs"
        private val RUST_CONSOLE_KEY: Key<Boolean> = Key("RS_CONSOLE_KEY")
    }
}
