/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.utils

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.DocumentAdapter
import com.intellij.util.Alarm
import javax.swing.event.DocumentEvent

class UiDebouncer(parentDisaposable: Disposable, private val delayMillis: Int = 200) {
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, parentDisaposable)

    fun <T> run(onPooledThread: () -> T, onUiThread: (T) -> Unit) {
        alarm.cancelAllRequests()
        val modalityState = ModalityState.current()
        alarm.addRequest({
            val r = onPooledThread()
            ApplicationManager.getApplication().invokeLater({ onUiThread(r) }, modalityState)
        }, delayMillis)
    }
}

fun pathToDirectoryTextField(
    disposable: Disposable,
    title: String,
    onTextChanged: () -> Unit = {}
): TextFieldWithBrowseButton {

    val component = TextFieldWithBrowseButton(null, disposable)
    component.addBrowseFolderListener(title, null, null,
        FileChooserDescriptorFactory.createSingleFolderDescriptor(),
        TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT
    )
    component.childComponent.document.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent?) {
            onTextChanged()
        }
    })

    return component
}
