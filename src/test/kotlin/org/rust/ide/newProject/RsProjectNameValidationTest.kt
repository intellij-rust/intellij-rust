/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.newProject

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class RsProjectNameValidationTest(
    private val name: String,
    private val isBinary: Boolean,
    private val isValid: Boolean
) {

    @Test
    fun test() {
        val message = RsPackageNameValidator.validate(name, isBinary)
        val assert: (String?) -> Unit = if (isValid) Assert::assertNull else Assert::assertNotNull
        assert(message)
    }

    companion object {
        @Parameterized.Parameters(name = "{index}: \"{0}\" is valid: {2}")
        @JvmStatic fun data(): Collection<Array<Any>> = listOf(
            arrayOf("valid-package_name", false, true),
            arrayOf("crate", false, false),
            arrayOf("build", false, true),
            arrayOf("build", true, false),
            arrayOf("1name", false, false),
            arrayOf("package.name", false, false),
            arrayOf("パッケージ", false , true),
            arrayOf("test", false, false),
            arrayOf("async", false, false),
            arrayOf("dyn", false, false),
            arrayOf("native", true, true)
        )
    }
}
