/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions.import

import org.intellij.lang.annotations.Language
import org.rust.ide.intentions.RsIntentionTestBase

abstract class ImportNameIntentionTestBase : RsIntentionTestBase(ImportNameIntention()) {

    protected fun doAvailableTestWithMultipleChoice(@Language("Rust") before: String,
                                                    expectedElements: Set<String>,
                                                    choice: String,
                                                    @Language("Rust") after: String) {
        withMockImportItemUi(object : ImportItemUi {
            override fun chooseItem(items: List<ImportCandidate>, callback: (ImportCandidate) -> Unit) {
                val actualItems = items.mapTo(HashSet()) { it.info.usePath }
                assertEquals(expectedElements, actualItems)
                val selectedValue = items.find { it.info.usePath == choice }
                    ?: error("Can't find `$choice` in `$actualItems`")
                callback(selectedValue)
            }
        }) { doAvailableTest(before, after) }
    }
}
