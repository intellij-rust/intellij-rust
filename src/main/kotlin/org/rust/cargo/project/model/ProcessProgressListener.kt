/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model

import com.intellij.build.events.BuildEventsNls
import com.intellij.execution.process.ProcessListener

@Suppress("UnstableApiUsage")
interface ProcessProgressListener : ProcessListener {
    fun error(@BuildEventsNls.Title title: String, @BuildEventsNls.Message message: String)
    fun warning(@BuildEventsNls.Title title: String, @BuildEventsNls.Message message: String)
}
