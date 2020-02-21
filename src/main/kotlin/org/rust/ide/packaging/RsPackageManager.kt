/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.packaging

import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.io.FileUtil
import java.io.File

interface RsPackageManager

abstract class RsPackageManagerBase(val sdk: Sdk): RsPackageManager {
    protected var separator: String = File.separator

    protected open fun getHelperPath(helper: String): String? =
        RsHelpersLocator.getHelperPath(helper)

    protected open fun getRustProcessOutput(
        helperPath: String,
        args: List<String>,
        askForSudo: Boolean,
        showProgress: Boolean,
        workingDir: String?
    ): ProcessOutput {
    }

    protected open fun toSystemDependentName(dirName: String): String =
        FileUtil.toSystemDependentName(dirName)
}
