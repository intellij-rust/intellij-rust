/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

import com.intellij.ide.util.importProject.ModuleDescriptor
import com.intellij.ide.util.importProject.ProjectDescriptor
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot
import com.intellij.ide.util.projectWizard.importSources.DetectedSourceRoot
import com.intellij.ide.util.projectWizard.importSources.ProjectFromSourcesBuilder
import com.intellij.ide.util.projectWizard.importSources.ProjectStructureDetector
import org.rust.cargo.toolchain.RustToolchain
import org.rust.ide.idea.CargoConfigurationWizardStep
import org.rust.ide.idea.RsModuleType
import java.io.File
import javax.swing.Icon

class RsProjectStructureDetector : ProjectStructureDetector() {
    override fun detectRoots(dir: File, children: Array<out File>, base: File, result: MutableList<DetectedProjectRoot>): DirectoryProcessingResult {
        if (children.any { it.name == RustToolchain.CARGO_TOML }) {
            result.add(object : DetectedProjectRoot(dir) {
                override fun getRootTypeName(): String = "Rust"
            })
        }

        return DirectoryProcessingResult.SKIP_CHILDREN
    }

    override fun setupProjectStructure(roots: MutableCollection<DetectedProjectRoot>, projectDescriptor: ProjectDescriptor, builder: ProjectFromSourcesBuilder) {
        val root = roots.singleOrNull()
        if (root == null || builder.hasRootsFromOtherDetectors(this) || projectDescriptor.modules.isNotEmpty()) {
            return
        }

        val moduleDescriptor = ModuleDescriptor(root.directory, RsModuleType.INSTANCE, emptyList<DetectedSourceRoot>())
        projectDescriptor.modules = listOf(moduleDescriptor)
    }

    override fun createWizardSteps(builder: ProjectFromSourcesBuilder,
                                   projectDescriptor: ProjectDescriptor,
                                   stepIcon: Icon?): List<ModuleWizardStep> =
        listOf(CargoConfigurationWizardStep.importExistingProject(builder.context, projectDescriptor))
}

