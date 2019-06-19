/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.notifications

import com.intellij.openapi.fileEditor.FileEditorManager
import org.rust.RsTestBase

abstract class RsNotificationProviderTestBase : RsTestBase() {

    protected abstract val notificationProvider: RsNotificationProvider

    protected fun doTest(filePath: String, expectedId: String? = null) {
        val file = myFixture.findFileInTempDir(filePath)!!
        val editor = FileEditorManager.getInstance(project).openFile(file, true)[0]
        val actualId = notificationProvider.createNotificationPanel(file, editor, project)?.debugId
        val message = when {
            actualId == null && expectedId != null -> "`$expectedId` notification not shown"
            actualId != null && expectedId == null -> "Unexpected `$actualId` notification"
            else -> ""
        }
        assertEquals(message, expectedId, actualId)
    }
}
