package org.rust.lang

import java.nio.file.Path
import java.nio.file.Paths

interface RustTestCase {

    fun getTestDataPath(): String

    companion object {
        val testResourcesPath = "src/test/resources"
    }
}


// Extensions

fun RustTestCase.pathToSourceTestFile(name: String): Path =
    Paths.get("${RustTestCase.testResourcesPath}/${getTestDataPath()}/$name.${RustFileType.DEFAULTS.EXTENSION}")

fun RustTestCase.pathToGoldTestFile(name: String): Path =
    Paths.get("${RustTestCase.testResourcesPath}/${getTestDataPath()}/$name.txt")

