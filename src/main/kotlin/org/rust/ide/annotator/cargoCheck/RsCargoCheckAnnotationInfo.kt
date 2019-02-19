/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.cargoCheck

import com.intellij.openapi.module.Module
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.toolchain.RustToolchain
import java.nio.file.Path

data class RsCargoCheckAnnotationInfo(
    val toolchain: RustToolchain,
    val projectPath: Path,
    val module: Module,
    val cargoPackage: CargoWorkspace.Package
)
