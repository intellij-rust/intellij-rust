/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.openapi.vfs.VirtualFile

fun checkResolvedFile(actualResolveFile: VirtualFile, expectedFilePath: String, pathResolver: (String) -> VirtualFile?): ResolveResult {
    if (expectedFilePath.startsWith("...")) {
        if (!actualResolveFile.path.endsWith(expectedFilePath.drop(3))) {
            return ResolveResult.Err("Should resolve to $expectedFilePath, was ${actualResolveFile.path} instead")
        }
    } else {
        val expectedResolveFile = pathResolver(expectedFilePath)
            ?: return ResolveResult.Err("Can't find `$expectedFilePath` file")

        if (actualResolveFile != expectedResolveFile) {
            return ResolveResult.Err("Should resolve to ${expectedResolveFile.path}, was ${actualResolveFile.path} instead")
        }
    }
    return ResolveResult.Ok
}

sealed class ResolveResult {
    object Ok : ResolveResult()
    data class Err(val message: String) : ResolveResult()
}
