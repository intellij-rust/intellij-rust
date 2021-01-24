/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.openapiext

import com.intellij.openapi.application.PathManager
import java.nio.file.Path
import java.nio.file.Paths

object RsPathManager {

    fun pluginDir(): Path = plugin().pluginPath
    fun prettyPrintersDir(): Path = pluginDir().resolve("prettyPrinters")

    fun pluginDirInSystem(): Path = Paths.get(PathManager.getSystemPath()).resolve("intellij-rust")
}

