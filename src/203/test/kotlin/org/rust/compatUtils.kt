/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.util.ThrowableRunnable

fun <T> withTestDialog(testDialog: TestDialog, action: () -> T): T {
    val oldDialog = TestDialogManager.setTestDialog(testDialog)
    return try {
        action()
    } finally {
        TestDialogManager.setTestDialog(oldDialog)
    }
}

typealias TestContext = ThrowableRunnable<Throwable>
