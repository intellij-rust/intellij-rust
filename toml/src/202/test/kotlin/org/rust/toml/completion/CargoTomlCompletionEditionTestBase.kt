/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import com.intellij.openapi.util.text.StringUtil
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestName

abstract class CargoTomlCompletionEditionTestBase : CargoTomlCompletionTestBase() {
    @Rule
    @JvmField
    public val nameRule: TestName = TestName()

    @Before
    fun before() {
        setName(StringUtil.substringBefore(nameRule.methodName, "["))
        setUp()
    }

    @After
    fun after() = tearDown()
}
