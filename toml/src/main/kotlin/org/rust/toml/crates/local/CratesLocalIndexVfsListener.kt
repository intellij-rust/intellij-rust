/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.crates.local

import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

class CratesLocalIndexVfsListener : AsyncFileListener {
    override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
        val service = CratesLocalIndexService.getInstanceIfCreated()
            as? CratesLocalIndexServiceImpl
            ?: return null

        if (!service.hasInterestingEvent(events)) return null

        return object : AsyncFileListener.ChangeApplier {
            override fun afterVfsChange() {
                service.updateIndex()
            }
        }
    }
}
