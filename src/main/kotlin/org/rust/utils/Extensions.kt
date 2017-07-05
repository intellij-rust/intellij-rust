/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.utils

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.systemIndependentPath
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Transforms seconds into milliseconds
 */
val Int.seconds: Int
    get() = this * 1000

// BACKCOMPAT: Kotlin API 1.0
fun String.toIntOrNull(): Int? = try {
   toInt()
} catch (e: NumberFormatException) {
    null
}

fun GeneralCommandLine(path: Path, vararg args: String) = GeneralCommandLine(path.systemIndependentPath, *args)
fun GeneralCommandLine.withWorkDirectory(path: Path?) = withWorkDirectory(path?.systemIndependentPath)
val VirtualFile.pathAsPath: Path get() = Paths.get(path)
