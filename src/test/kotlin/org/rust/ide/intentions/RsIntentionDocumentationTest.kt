/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.codeInsight.intention.IntentionManager
import org.rust.lang.RsTestBase

class RsIntentionDocumentationTest : RsTestBase() {

    fun `test intentions has documentation`() {
        IntentionManager.EP_INTENTION_ACTIONS
            .extensions
            .filter { it.category?.startsWith("Rust") == true }
            .forEach {
                val simpleName = it.className.substringAfterLast(".")
                val directory = "intentionDescriptions/$simpleName"
                val files = listOf("before.rs.template", "after.rs.template", "description.html")
                for (file in files) {
                    val text = getResourceAsString("$directory/$file")
                        ?: fail("No inspection description for ${it.className}.\n" +
                            "Add ${files.joinToString()} to src/main/resources/$directory")

                    if (file.endsWith(".html")) {
                        checkHtmlStyle(text)
                    }
                }
            }
    }
}
