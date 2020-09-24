/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.util.ThrowableRunnable

// BACKCOMPAT: 2020.2. Merge with `RsCompletionAutoPopupTestBase`
class RsCompletionAutoPopupTest : RsCompletionAutoPopupTestBase() {

    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        tester.runWithAutoPopupEnabled(testRunnable)
    }
}
