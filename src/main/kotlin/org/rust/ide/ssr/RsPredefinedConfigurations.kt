/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.ssr

import com.intellij.structuralsearch.PredefinedConfigurationUtil
import com.intellij.structuralsearch.plugin.ui.Configuration
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.rust.RsBundle
import org.rust.lang.RsFileType

object RsPredefinedConfigurations {
    private val STRUCT_TYPE get() = "Rust/Structs"
    private val DECLARATIONS_TYPE get() = "Rust/Declarations"

    private fun searchTemplate(
        @Nls name: String,
        @NonNls refName: String,
        @NonNls pattern: String,
        @Nls category: String,
    ) = PredefinedConfigurationUtil.createConfiguration(name, refName, pattern, category, RsFileType)

    fun createPredefinedTemplates(): Array<Configuration> = arrayOf(
        // Declarations
        searchTemplate(
            RsBundle.message("constants.equal.to.1"),
            "constants = 1",
            """
                const 'Name\: '_t = 1;
            """.trimIndent(),
            DECLARATIONS_TYPE,
        ),

        // Structs
        searchTemplate(
            RsBundle.message("structs.deriving.default"),
            "structs deriving default",
            """
                #[derive(Default)]
                struct 'Name
            """.trimIndent(),
            STRUCT_TYPE,
        ),
        searchTemplate(
            RsBundle.message("structs.with.a.u8.field"),
            "structs with a u8 field",
            """
                struct 'Name {
                    '_before*,
                    '_field\: u8,
                    '_after*,
                }
            """.trimIndent(),
            STRUCT_TYPE,
        ),
    )

}
