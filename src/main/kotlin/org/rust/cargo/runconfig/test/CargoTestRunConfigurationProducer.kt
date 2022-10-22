/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.test

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.ide.injected.DoctestInfo
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsModDeclItem
import org.rust.lang.core.psi.ext.*
import org.rust.lang.doc.psi.RsDocCodeFence
import org.rust.openapiext.isUnitTestMode
import org.rust.openapiext.pathAsPath
import org.rust.stdext.capitalized

class CargoTestRunConfigurationProducer : CargoTestRunConfigurationProducerBase() {
    override val commandName: String = "test"

    init {
        registerConfigProvider { elements, climbUp -> createConfigFor<RsModDeclItem>(elements, climbUp) }
        registerConfigProvider { elements, climbUp -> createConfigForDocTest(elements, climbUp) }
        registerConfigProvider { elements, climbUp -> createConfigFor<RsFunction>(elements, climbUp) }
        registerConfigProvider { elements, climbUp -> createConfigFor<RsMod>(elements, climbUp) }
        registerConfigProvider { elements, climbUp -> createConfigForMultipleFiles(elements, climbUp) }
        registerDirectoryConfigProvider { dir -> createConfigForDirectory(dir) }
        registerDirectoryConfigProvider { dir -> createConfigForCargoProject(dir) }
        registerDirectoryConfigProvider { dir -> createConfigForCargoPackage(dir) }
    }

    override fun isSuitable(element: PsiElement): Boolean =
        when (element) {
            is RsMod -> hasTestFunction(element)
            is RsFunction -> element.isTest
            else -> false
        }

    private fun createConfigForCargoProject(dir: PsiDirectory): TestConfig? {
        val dirPath = dir.virtualFile.pathAsPath
        val cargoProject = dir.findCargoProject() ?: return null
        if (dirPath != cargoProject.workingDirectory) return null
        return CargoProjectTestConfig(commandName, dir, cargoProject)
    }

    private fun createConfigForCargoPackage(dir: PsiDirectory): TestConfig? {
        val dirPath = dir.virtualFile.pathAsPath
        val cargoPackage = dir.findCargoPackage() ?: return null
        if (dirPath != cargoPackage.rootDirectory || cargoPackage.origin != PackageOrigin.WORKSPACE) return null
        return CargoPackageTestConfig(commandName, dir, cargoPackage)
    }

    private fun createConfigForDocTest(
        elements: List<PsiElement>,
        climbUp: Boolean
    ): TestConfig? {
        val (codeFence, ctx) = elements
            .mapNotNull {
                val originalElement = findElement<RsDocCodeFence>(it, climbUp) ?: return@mapNotNull null
                if (originalElement.containingCargoTarget == null) return@mapNotNull null

                val ctx = originalElement.getDoctestCtx() ?: return@mapNotNull null
                originalElement to ctx
            }
            .singleOrNull()
            ?: return null
        val target = codeFence.containingCargoTarget ?: return null
        val ownerPath = ctx.owner.crateRelativePath.configPath() ?: return null

        return DocTestConfig(
            commandName, ownerPath, target, codeFence, ctx
        )
    }

    companion object {
        private fun hasTestFunction(mod: RsMod): Boolean =
            mod.processExpandedItemsExceptImplsAndUses { it is RsFunction && it.isTest || it is RsMod && hasTestFunction(it) }
    }
}

private class CargoProjectTestConfig(
    override val commandName: String,
    override val sourceElement: PsiDirectory,
    val cargoProject: CargoProject
) : TestConfig {
    override val targets: List<CargoWorkspace.Target> = emptyList()
    override val path: String = ""
    override val exact = false

    override val configurationName: String
        get() = "All ${StringUtil.pluralize(commandName).capitalized()}"

    override fun cargoCommandLine(): CargoCommandLine =
        CargoCommandLine.forProject(cargoProject, commandName)
}

private class CargoPackageTestConfig(
    override val commandName: String,
    override val sourceElement: PsiDirectory,
    val cargoPackage: CargoWorkspace.Package
) : TestConfig {
    override val targets: List<CargoWorkspace.Target> = emptyList()
    override val path: String = ""
    override val exact = false

    override val configurationName: String
        get() = "${StringUtil.pluralize(commandName).capitalized()} in '${sourceElement.name}'"

    override fun cargoCommandLine(): CargoCommandLine =
        CargoCommandLine.forPackage(cargoPackage, commandName)
}

class DocTestContext(val owner: RsQualifiedNamedElement, val lineNumber: Int, val isIgnored: Boolean)

// If we encounter one of these attributes, it's probably a doctest.
// If we encounter something else, it's probably a code block written in some other language.
// https://doc.rust-lang.org/rustdoc/write-documentation/documentation-tests.html
private val KNOWN_DOCTEST_ATTRIBUTES = setOf(
    "compile_fail",
    "ignore",
    "rust",
    "should_panic",
    "no_run",
    "test_harness",
    "allow_fail",
    "edition2015",
    "edition2018",
    "edition2021"
)

fun RsDocCodeFence.getDoctestCtx(): DocTestContext? {
    if (containingCrate?.areDoctestsEnabled != true) return null
    if (DoctestInfo.hasUnbalancedCodeFencesBefore(this)) return null

    val owner = containingDoc.owner as? RsQualifiedNamedElement ?: return null

    val containingFile = originalElement.containingFile
    val project = containingFile.project
    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    val document = psiDocumentManager.getDocument(containingFile) ?: return null
    val textOffset = originalElement.startOffset

    // Cargo uses 1-based line numbers
    val lineNumber = document.getLineNumber(textOffset) + 1

    var text = lang?.text?.trim().orEmpty()
    // Ignore test line marker comments in tests
    if (isUnitTestMode && "// -" in text) {
        text = text.substring(0, text.indexOf("// -")).trim()
    }

    val tags = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (tags.isNotEmpty() && !KNOWN_DOCTEST_ATTRIBUTES.contains(tags.first())) return null

    val isIgnored = tags.contains("ignore")
    return DocTestContext(owner, lineNumber, isIgnored)
}

private class DocTestConfig(
    override val commandName: String,
    private val ownerPath: String,
    val target: CargoWorkspace.Target,
    override val sourceElement: RsDocCodeFence,
    val ctx: DocTestContext
) : TestConfig {
    override val isIgnored: Boolean = ctx.isIgnored

    // `cargo test` exact matching doesn't work with the escaped spaces, see below
    override val exact: Boolean = false
    override val isDoctest: Boolean = true

    override val path: String
        get() = buildString {
            // `cargo test` uses a regex for matching the test names.
            // Doctests contain spaces in their name (e.g. `foo::bar (line X)`).
            // To make test lookup work, we need to escape spaces in the test path.
            if (ownerPath.isNotEmpty()) {
                append("${ownerPath}\\ ")
            }
            append("(line\\ ${ctx.lineNumber})")
        }

    override val targets: List<CargoWorkspace.Target>
        get() = listOf(target)

    override val configurationName: String
        get() = buildString {
            append("Doctest of ")
            if (ownerPath.isNotEmpty()) {
                append(ownerPath)
            }
            else {
                // The owner is a crate root library module
                val name = ctx.owner.containingCargoPackage?.name ?: ctx.owner.containingFile.name
                append(name)
            }
            append(" (line ")
            append(ctx.lineNumber)
            append(")")
        }
}
