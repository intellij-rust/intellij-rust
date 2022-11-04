/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.rust.RsTask.TaskType.*
import org.rust.util.RsBackgroundTaskQueue

/**
 * A common queue for cargo and macro expansion tasks that should be executed sequentially.
 * Can run any [Task.Backgroundable], but provides additional features for tasks that implement [RsTask].
 * The most important feature is that newly submitted tasks can cancel a currently running task or
 * tasks in the queue (See [RsTask.taskType]).
 */
@Service
class RsProjectTaskQueueService : Disposable {
    private val queue: RsBackgroundTaskQueue = RsBackgroundTaskQueue()

    /** Submits a task. A task can implement [RsTask] */
    fun run(task: Task.Backgroundable) = queue.run(task)

    /** Equivalent to running an empty task with [RsTask.taskType] = [taskType] */
    fun cancelTasks(taskType: RsTask.TaskType) = queue.cancelTasks(taskType)

    /** @return true if no running or pending tasks */
    val isEmpty: Boolean get() = queue.isEmpty

    override fun dispose() {
        queue.dispose()
    }
}

val Project.taskQueue: RsProjectTaskQueueService get() = service()

interface RsTask {
    val taskType: TaskType
        get() = INDEPENDENT

    val progressBarShowDelay: Int
        get() = 0

    /** If true, the task will not be run (and progress bar will not be shown) until the smart mode */
    val waitForSmartMode: Boolean
        get() = false

    val runSyncInUnitTests: Boolean
        get() = false

    /**
     * Higher position in the enum means higher priority; Newly submitted tasks with higher or equal
     * priority cancels other tasks with lower or equal priority if [canBeCanceledByOther] == true.
     * E.g. [CARGO_SYNC] cancels [MACROS_UNPROCESSED] and subsequent but not [MACROS_CLEAR] or itself.
     * [MACROS_UNPROCESSED] cancels itself, [MACROS_FULL] and subsequent.
     */
    enum class TaskType(val canBeCanceledByOther: Boolean = true) {
        CARGO_SYNC(canBeCanceledByOther = false),
        MACROS_CLEAR(canBeCanceledByOther = false),
        MACROS_UNPROCESSED,
        MACROS_FULL,

        /** Can't be canceled, cancels nothing. Should be the last variant of the enum. */
        INDEPENDENT(canBeCanceledByOther = false);

        fun canCancelOther(other: TaskType): Boolean =
            other.canBeCanceledByOther && this.ordinal <= other.ordinal
    }
}

