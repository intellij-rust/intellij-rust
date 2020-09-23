/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import org.junit.After
import org.junit.Before

abstract class CargoTomlCompletionEditionTestBase : CargoTomlCompletionTestBase() {

    @Before
    fun before() = setUp()

    @After
    fun after() = tearDown()
}
