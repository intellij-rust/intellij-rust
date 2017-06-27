/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang

import java.nio.file.Path
import java.nio.file.Paths

interface RsTestCase {

    fun getTestDataPath(): String

    companion object {
        val testResourcesPath = "src/test/resources"
    }
}


fun RsTestCase.pathToSourceTestFile(name: String): Path =
    Paths.get("${RsTestCase.testResourcesPath}/${getTestDataPath()}/$name.${RsFileType.DEFAULTS.EXTENSION}")

fun RsTestCase.pathToGoldTestFile(name: String): Path =
    Paths.get("${RsTestCase.testResourcesPath}/${getTestDataPath()}/$name.txt")

