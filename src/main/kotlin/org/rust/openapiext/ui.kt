/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.openapiext

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts.DialogTitle
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.Alarm
import org.rust.ide.intentions.util.macros.RsIntentionInsideMacroExpansionEditor
import org.rust.lang.RsFileType
import org.rust.lang.core.psi.ext.RsElement
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

class UiDebouncer(
    private val parentDisposable: Disposable,
    private val delayMillis: Int = 200
) {
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, parentDisposable)

    /**
     * @param onUiThread: callback to be executed in EDT with **any** modality state.
     * Use it only for UI updates
     */
    fun <T> run(onPooledThread: () -> T, onUiThread: (T) -> Unit) {
        if (Disposer.isDisposed(parentDisposable)) return
        alarm.cancelAllRequests()
        alarm.addRequest({
            val r = onPooledThread()
            invokeLater(ModalityState.any()) {
                if (!Disposer.isDisposed(parentDisposable)) {
                    onUiThread(r)
                }
            }
        }, delayMillis)
    }
}

fun pathToDirectoryTextField(
    disposable: Disposable,
    @Suppress("UnstableApiUsage") @DialogTitle title: String,
    onTextChanged: () -> Unit = {}
): TextFieldWithBrowseButton =
    pathTextField(
        FileChooserDescriptorFactory.createSingleFolderDescriptor(),
        disposable,
        title,
        onTextChanged
    )

fun pathToRsFileTextField(
    disposable: Disposable,
    @DialogTitle title: String,
    project: Project,
    onTextChanged: () -> Unit = {}
): TextFieldWithBrowseButton =
    pathTextField(
        FileChooserDescriptorFactory
            .createSingleFileDescriptor(RsFileType)
            .withRoots(project.guessProjectDir()),
        disposable,
        title,
        onTextChanged
    )

fun pathTextField(
    fileChooserDescriptor: FileChooserDescriptor,
    disposable: Disposable,
    @DialogTitle title: String,
    onTextChanged: () -> Unit = {}
): TextFieldWithBrowseButton {
    val component = TextFieldWithBrowseButton(null, disposable)
    component.addBrowseFolderListener(
        title, null, null,
        fileChooserDescriptor,
        TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT
    )
    component.childComponent.addTextChangeListener { onTextChanged() }
    return component
}

fun JTextField.addTextChangeListener(listener: (DocumentEvent) -> Unit) {
    document.addDocumentListener(
        object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                listener(e)
            }
        }
    )
}

fun selectElement(element: RsElement, editor: Editor) {
    val start = element.textRange.startOffset
    val unwrappedEditor = if (editor is RsIntentionInsideMacroExpansionEditor && element.containingFile != editor.psiFileCopy) {
        if (element.containingFile != editor.originalFile) return
        editor.originalEditor
    } else {
        editor
    }
    unwrappedEditor.caretModel.moveToOffset(start)
    unwrappedEditor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    unwrappedEditor.selectionModel.setSelection(start, element.textRange.endOffset)
}

fun <T : JComponent> Row.fullWidthCell(component: T): Cell<T> {
    return cell(component)
        .horizontalAlign(HorizontalAlign.FILL)
}

val JBTextField.trimmedText: String
    get() = text.trim()
