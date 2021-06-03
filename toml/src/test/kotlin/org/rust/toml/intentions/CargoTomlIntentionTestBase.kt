/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.intentions

import com.intellij.codeInsight.intention.IntentionAction
import org.intellij.lang.annotations.Language
import org.rust.ide.intentions.RsIntentionTestBase
import kotlin.reflect.KClass

abstract class CargoTomlIntentionTestBase(intentionClass: KClass<out IntentionAction>) : RsIntentionTestBase(intentionClass) {
    protected fun doAvailableTest(
        @Language("TOML") before: String,
        @Language("TOML") after: String
    ) = doAvailableTest(before, after, "Cargo.toml")

    protected fun doUnavailableTest(@Language("TOML") before: String) =
        doUnavailableTest(before, "Cargo.toml")

    protected fun checkAvailableInSelectionOnly(@Language("TOML") code: String) =
        checkAvailableInSelectionOnly(code, "Cargo.toml")
}
