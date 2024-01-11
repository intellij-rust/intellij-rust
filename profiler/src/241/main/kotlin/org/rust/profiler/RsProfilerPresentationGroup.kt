/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.profiler

import com.intellij.profiler.clion.ProfilerPresentationGroup
import com.intellij.xdebugger.attach.XAttachProcessPresentationGroup

class RsProfilerPresentationGroup : ProfilerPresentationGroup {
    override fun getPresentationGroup(): XAttachProcessPresentationGroup = RsAttachPresentationGroup
}
