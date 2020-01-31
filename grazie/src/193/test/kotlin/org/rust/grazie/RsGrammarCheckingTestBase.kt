/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.grazie

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.GrazieInspection
import com.intellij.grazie.jlanguage.LangDetector
import org.rust.ide.inspections.RsInspectionsTestBase

abstract class RsGrammarCheckingTestBase : RsInspectionsTestBase(GrazieInspection::class) {

    override fun setUp() {
        super.setUp()
        LangDetector.init(GrazieConfig.get(), project)
    }
}
