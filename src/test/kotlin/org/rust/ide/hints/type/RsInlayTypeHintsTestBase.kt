/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.type

import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsProviderExtension
import com.intellij.codeInsight.hints.InlayHintsSettings
import com.intellij.codeInsight.hints.LinearOrderInlayRenderer
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.RsLanguage
import kotlin.reflect.KClass

@Suppress("UnstableApiUsage")
abstract class RsInlayTypeHintsTestBase(
    private val providerClass: KClass<out InlayHintsProvider<*>>
) : RsTestBase() {

    override fun setUp() {
        super.setUp()
        changeHintsProviderStatuses { it::class == providerClass }
    }

    override fun tearDown() {
        changeHintsProviderStatuses { true }
        super.tearDown()
    }

    protected fun checkByText(@Language("Rust") code: String) {
        InlineFile(code.replace(HINT_COMMENT_PATTERN, "<$1/>"))
        checkInlays()
    }

    protected fun checkInlays() {
        myFixture.testInlays(
            { (it.renderer as LinearOrderInlayRenderer<*>).toString() },
            { it.renderer is LinearOrderInlayRenderer<*> }
        )
    }

    companion object {
        private val HINT_COMMENT_PATTERN = Regex("""/\*(hint.*?)\*/""")

        private fun changeHintsProviderStatuses(statusGetter: (InlayHintsProvider<*>) -> Boolean) {
            val settings = InlayHintsSettings.instance()
            InlayHintsProviderExtension.findProviders()
                .filter { it.language == RsLanguage }
                .forEach { settings.changeHintTypeStatus(it.provider.key, it.language, statusGetter(it.provider)) }
        }
    }
}
