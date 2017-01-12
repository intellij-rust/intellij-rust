package org.rust.lang

import java.nio.file.Path
import java.nio.file.Paths

interface RsTestCase {

    fun getTestDataPath(): String

    companion object {
        val testResourcesPath = "src/test/resources"
    }
}


// Extensions

fun RsTestCase.pathToSourceTestFile(name: String): Path =
    Paths.get("${RsTestCase.testResourcesPath}/${getTestDataPath()}/$name.${RustFileType.DEFAULTS.EXTENSION}")

fun RsTestCase.pathToGoldTestFile(name: String): Path =
    Paths.get("${RsTestCase.testResourcesPath}/${getTestDataPath()}/$name.txt")

