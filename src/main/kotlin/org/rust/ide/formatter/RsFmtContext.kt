/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter

import com.intellij.formatting.Alignment
import com.intellij.formatting.SpacingBuilder
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.rust.ide.formatter.impl.createSpacingBuilder
import org.rust.ide.formatter.impl.isFlatBlock
import org.rust.ide.formatter.settings.RsCodeStyleSettings
import org.rust.lang.RsLanguage

data class RsFmtContext private constructor(
    val commonSettings: CommonCodeStyleSettings,
    val rustSettings: RsCodeStyleSettings,
    val spacingBuilder: SpacingBuilder,

    /**
     * Stores shared alignment object, e.g. for function declarations's parameters, return type & where clause.
     */
    val sharedAlignment: Alignment? = null,

    /**
     * Determine whether we have spotted opening delimiter during
     * construction of a _flat block_'s sub blocks list.
     *
     * We only care about opening delimiters (`(`, `[`, `{`, `<`, `|`) here,
     * because none of flat blocks has any children after block part (apart
     * from closing delimiter, which we have to handle separately anyways).
     *
     * @see isFlatBlock
     */
    val metLBrace: Boolean = false
) {
    companion object {
        fun create(settings: CodeStyleSettings): RsFmtContext {
            val commonSettings = settings.getCommonSettings(RsLanguage)
            val rustSettings = settings.rust
            return RsFmtContext(commonSettings, settings.rust, createSpacingBuilder(commonSettings, rustSettings))
        }
    }
}
