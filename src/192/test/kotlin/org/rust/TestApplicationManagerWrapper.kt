/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.idea.IdeaTestApplication
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider

@Suppress("UnstableApiUsage")
class TestApplicationManagerWrapper private constructor(private val delegate: IdeaTestApplication) {

    fun setDataProvider(provider: DataProvider?, parentDisposable: Disposable) {
        delegate.setDataProvider(provider, parentDisposable)
    }

    companion object {
        fun getInstance(): TestApplicationManagerWrapper {
            return TestApplicationManagerWrapper(IdeaTestApplication.getInstance())
        }
    }
}
