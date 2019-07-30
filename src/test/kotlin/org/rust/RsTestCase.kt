/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import org.rust.lang.RsFileType
import java.nio.file.Path
import java.nio.file.Paths

interface RsTestCase {

    fun getTestDataPath(): String

    companion object {
        val testResourcesPath = "src/test/resources"
        // See `rustc --print cfg` for some defaults
        // We do not want to set "test" inside Rust stdlib as this disables some important parts
        // TODO: Is this safe for initialization?
        val testDefaultCfgStdLib = listOf("debug_assertions",
            "target_has_atomic=\"16\"",
            "target_has_atomic=\"32\"",
            "target_has_atomic=\"64\"",
            "target_has_atomic=\"8\"",
            "target_has_atomic=\"cas\"",
            "target_has_atomic=\"ptr\"",
            "target_arch=\"x86_64\"",
            "target_endian=\"little\"",
            "target_env=\"gnu\"",
            "target_family=\"unix\"",
            "target_os=\"linux\"",
            "target_pointer_width=\"64\"",
            "unix",
            "feature=\"use_std\"" /*Not enabled normally*/)
        val testDefaultCfg = testDefaultCfgStdLib.toMutableList() + "test"
    }
}


fun RsTestCase.pathToSourceTestFile(name: String): Path =
    Paths.get("${RsTestCase.testResourcesPath}/${getTestDataPath()}/$name.${RsFileType.DEFAULTS.EXTENSION}")

fun RsTestCase.pathToGoldTestFile(name: String): Path =
    Paths.get("${RsTestCase.testResourcesPath}/${getTestDataPath()}/$name.txt")

