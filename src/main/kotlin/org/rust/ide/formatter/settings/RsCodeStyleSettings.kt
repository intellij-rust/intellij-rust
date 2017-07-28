/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter.settings

import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings

class RsCodeStyleSettings(container: CodeStyleSettings) :
    CustomCodeStyleSettings(RsCodeStyleSettings::class.java.simpleName, container) {

    @JvmField var ALIGN_RET_TYPE = true
    @JvmField var ALIGN_WHERE_CLAUSE = false
    @JvmField var ALIGN_TYPE_PARAMS = false
    @JvmField var ALIGN_WHERE_BOUNDS = true
    @JvmField var ALLOW_ONE_LINE_MATCH = false
    @JvmField var MIN_NUMBER_OF_BLANKS_BETWEEN_ITEMS = 1
    @JvmField var PRESERVE_PUNCTUATION = false
}
