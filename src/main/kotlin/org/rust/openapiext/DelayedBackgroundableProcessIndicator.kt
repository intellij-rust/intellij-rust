/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.openapiext

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.TaskInfo
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.util.ui.UIUtil

/**
 * [BackgroundableProcessIndicator] has a method [BackgroundableProcessIndicator.setDelayInMillis] to postpone
 * progress bar displaying in UI, but it doesn't work (!) for background progress bar and works for modal windows
 * only. This class provides a hack to make it work. First, we pretend that want to show a modal window
 * ([PerformInBackgroundOption.DEAF] constructor parameter). Then, we override [prepareShowDialog] with nothing
 * and because of this the modal window will not actually be shown. Then, we run the timer, which in `delay`
 * milliseconds will invoke [background] that will show the "background" progress bar in the status bar.
 */
class DelayedBackgroundableProcessIndicator(task: Task.Backgroundable, delay: Int) :
    BackgroundableProcessIndicator(
        task.project,
        task,
        // "Show in a modal window" (the window won't really be shown, see prepareShowDialog)
        PerformInBackgroundOption.DEAF
    ) {

    @Volatile
    private var isFinishCalled = false

    init {
        // BACKCOMPAT: 2019.2
        @Suppress("DEPRECATION")
        val timer = UIUtil.createNamedTimer("DelayedBackgroundableProcessIndicator timer", delay) {
            ApplicationManager.getApplication().invokeLater({
                if (isRunning && !isFinishCalled) {
                    background()
                }
            }, modalityState)
        }
        timer.isRepeats = false
        timer.start()
    }

    override fun prepareShowDialog() {
        // Don't show the modal window
    }

    override fun finish(task: TaskInfo) {
        isFinishCalled = true
        super.finish(task)
    }
}
