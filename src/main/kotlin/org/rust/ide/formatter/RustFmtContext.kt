package org.rust.ide.formatter

import com.intellij.formatting.Alignment
import com.intellij.formatting.SpacingBuilder
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.rust.ide.formatter.impl.createSpacingBuilder
import org.rust.ide.formatter.impl.isFlatBlock
import org.rust.ide.formatter.settings.RustCodeStyleSettings
import org.rust.lang.RustLanguage

data class RustFmtContext private constructor(
    val commonSettings: CommonCodeStyleSettings,
    val rustSettings: RustCodeStyleSettings,
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
        fun create(settings: CodeStyleSettings): RustFmtContext {
            val commonSettings = settings.getCommonSettings(RustLanguage)
            val rustSettings = settings.getCustomSettings(RustCodeStyleSettings::class.java)
            return RustFmtContext(commonSettings, rustSettings, createSpacingBuilder(commonSettings, rustSettings))
        }
    }
}
