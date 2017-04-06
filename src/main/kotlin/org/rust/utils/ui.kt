package org.rust.utils

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.util.Alarm

class UiDebouncer(parentDisaposable: Disposable, private val delayMillis: Int = 200) {
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, parentDisaposable)

    fun <T> run(onPooledThread: () -> T, onUiThread: (T) -> Unit) {
        alarm.cancelAllRequests()
        alarm.addRequest({
            val r = onPooledThread()
            ApplicationManager.getApplication().invokeLater({ onUiThread(r) }, ModalityState.any())
        }, delayMillis)
    }
}
