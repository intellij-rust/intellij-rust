/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.openapiext

import com.intellij.build.events.BuildEventsNls
import org.jetbrains.annotations.Nls

sealed class TaskResult<out T> {
    class Ok<out T>(val value: T) : TaskResult<T>()
    class Err<out T>(@Nls val reason: String, @BuildEventsNls.Message val message: String? = null) : TaskResult<T>()
}
