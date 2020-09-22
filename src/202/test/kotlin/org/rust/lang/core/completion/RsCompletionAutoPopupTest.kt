/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsCompletionAutoPopupTest : RsCompletionAutoPopupTestBase() {

    override fun invokeTestRunnable(runnable: Runnable) {
        tester.runWithAutoPopupEnabled(runnable)
    }
}
