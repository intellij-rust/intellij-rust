/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.statistics

import com.intellij.internal.statistic.fileTypes.FileTypeStatisticProvider
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.fileTypes.FileType
import org.rust.lang.RsFileType
import org.rust.openapiext.PLUGIN_ID

class RsFileTypeStatisticProvider : FileTypeStatisticProvider {
    override fun getPluginId(): String = PLUGIN_ID
    override fun accept(event: EditorFactoryEvent, fileType: FileType): Boolean = fileType == RsFileType
}
