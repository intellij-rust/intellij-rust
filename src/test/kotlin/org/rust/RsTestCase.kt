/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.TestCase

interface RsTestCase : TestCase {
    override val testFileExtension: String get() = "rs"
}
