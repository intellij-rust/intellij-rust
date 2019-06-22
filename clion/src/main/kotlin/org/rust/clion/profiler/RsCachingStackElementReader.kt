/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.profiler

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.profiler.api.BaseCallStackElement
import com.intellij.profiler.model.BaseCachingStackElementReader
import com.intellij.profiler.model.CantBeParsedCall
import org.rust.clion.profiler.dtrace.RsDTraceNavigatableNativeCall

@Suppress("UnstableApiUsage")
class RsCachingStackElementReader : BaseCachingStackElementReader() {

    fun parseStackElement(string: String): BaseCallStackElement {
        val call = RsDTraceNavigatableNativeCall.read(string, stringsInterner) ?: CantBeParsedCall(string)
        return intern(call)
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project) = project.service<RsCachingStackElementReader>()
    }
}
