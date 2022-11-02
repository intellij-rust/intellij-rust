/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.util.messages.Topic

interface MacroExpansionTaskListener {
    fun onMacroExpansionTaskFinished()

    companion object {
        @JvmStatic
        val MACRO_EXPANSION_TASK_TOPIC: Topic<MacroExpansionTaskListener> = Topic(
            "rust macro expansion task",
            MacroExpansionTaskListener::class.java
        )
    }
}
