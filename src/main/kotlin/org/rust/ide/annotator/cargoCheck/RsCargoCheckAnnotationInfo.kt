/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.cargoCheck

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.project.Project
import org.rust.cargo.toolchain.RustToolchain
import java.nio.file.Path

data class RsCargoCheckAnnotationInfo(
    val toolchain: RustToolchain,
    val project: Project,
    val owner: ComponentManager,
    val workingDirectory: Path,
    val packageName: String? = null
)
