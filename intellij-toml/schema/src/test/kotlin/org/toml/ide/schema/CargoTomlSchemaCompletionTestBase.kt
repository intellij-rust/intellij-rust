/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.schema

import com.intellij.codeInsight.lookup.LookupElement
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import org.intellij.lang.annotations.Language
import org.toml.TomlTestBase
import org.toml.ide.completion.TomlCompletionFixture

abstract class CargoTomlSchemaCompletionTestBase : TomlTestBase() {
    lateinit var completionFixture: TomlCompletionFixture

    override fun setUp() {
        super.setUp()
        completionFixture = TomlCompletionFixture(myFixture, "Cargo.toml")
        completionFixture.setUp()
        JsonSchemaProviderFactory.EP_NAME.point.registerExtension(CargoTomlSchemaProviderFactory(), testRootDisposable)
    }

    override fun tearDown() {
        completionFixture.tearDown()
        super.tearDown()
    }

    fun checkContainsCompletion(
        variants: List<String>,
        @Language("TOML") code: String,
        render: LookupElement.() -> String = { lookupString }
    ) = completionFixture.checkContainsCompletion(variants, code, render)
}
