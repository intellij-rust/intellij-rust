/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.runAnything

import com.intellij.ide.actions.runAnything.items.RunAnythingItemBase
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.execution.ParametersListUtil
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.Icon
import javax.swing.JPanel

class RunAnythingCargoItem(command: String, icon: Icon) : RunAnythingItemBase(command, icon) {

    override fun createComponent(pattern: String?, isSelected: Boolean, hasFocus: Boolean): Component {
        return super.createComponent(pattern, isSelected, hasFocus).also(this::customizeComponent)
    }

    private fun customizeComponent(component: Component) {
        if (component !is JPanel) return

        val params = ParametersListUtil.parse(StringUtil.trimStart(command, "cargo"))
        val description = when (params.size) {
            0 -> null
            1 -> commandDescriptions[params.last()]
            else -> {
                val optionsDescriptions = getOptionsDescriptionsForCommand(params.first())
                optionsDescriptions?.get(params.last())
            }
        } ?: return
        val descriptionComponent = SimpleColoredComponent()
        descriptionComponent.append(
            StringUtil.shortenTextWithEllipsis(" $description.", 200, 0),
            SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES
        )
        component.add(descriptionComponent, BorderLayout.EAST)
    }

    companion object {
        private val commandDescriptions: Map<String, String> =
            hashMapOf(
                "build" to "Compile the current project",
                "check" to "Analyze the current project and report errors, but don't build object files",
                "clean" to "Remove the target directory",
                "doc" to "Build this project's and its dependencies' documentation",
                "new" to "Create a new cargo project",
                "init" to "Create a new cargo project in an existing directory",
                "run" to "Build and execute src/main.rs",
                "test" to "Run the tests",
                "bench" to "Run the benchmarks",
                "update" to "Update dependencies listed in Cargo.lock",
                "search" to "Search registry for crates",
                "publish" to "Package and upload this project to the registry",
                "install" to "Install a Rust binary",
                "uninstall" to "Uninstall a Rust binary"
            )

        fun getOptionsDescriptionsForCommand(commandName: String): Map<String, String>? =
            when (commandName) {
                "build" -> buildOptionsDescriptions
                "check" -> checkOptionsDescriptions
                "clean" -> cleanOptionsDescriptions
                "doc" -> docOptionsDescriptions
                "run" -> runOptionsDescriptions
                "test" -> testOptionsDescriptions
                "bench" -> benchOptionsDescriptions
                "update" -> updateOptionsDescriptions
                "search" -> searchOptionsDescriptions
                "publish" -> publishOptionsDescriptions
                "install" -> installOptionsDescriptions
                "uninstall" -> uninstallOptionsDescriptions
                else -> null
            }

        private val jobs: Pair<String, String> = "--jobs" to "Number of parallel jobs, defaults to # of CPUs"
        private val exclude: Pair<String, String> = "--exclude" to "Exclude packages from the build"
        private val index: Pair<String, String> = "--index" to "Registry index to upload the package to"
        private val release: Pair<String, String> = "--release" to "Build artifacts in release mode, with optimizations"
        private val registry: Pair<String, String> = "--registry" to "Registry to use"
        private val target: Pair<String, String> = "--target" to "Build for the target triple"
        private val targetDir: Pair<String, String> = "--target-dir" to "Directory for all generated artifacts"
        private val manifestPath: Pair<String, String> = "--manifest-path" to "Path to Cargo.toml"
        private val messageFormat: Pair<String, String> =
            "--message-format" to "Error format [default: human]  [possible values: human, json, short]"

        private val common: Map<String, String> =
            hashMapOf(
                "--verbose" to "Use verbose output (-vv very verbose/build.rs output)",
                "--quiet" to "No output printed to stdout",
                "--color" to "Coloring: auto, always, never",
                "--frozen" to "Require Cargo.lock and cache are up to date",
                "--locked" to "Require Cargo.lock is up to date",
                "--help" to "Prints help information"
            )

        private val features: Map<String, String> =
            hashMapOf(
                "--features" to "Space-separated list of features to activate",
                "--all-features" to "Activate all available features",
                "--no-default-features" to "Do not activate the `default` feature"
            )

        private val buildOptionsDescriptions: Map<String, String> =
            hashMapOf(
                "--package" to "Package to build",
                "--all" to "Build all packages in the workspace",
                exclude,
                jobs,
                "--lib" to "Build only this package's library",
                "--bin" to "Build only the specified binary",
                "--bins" to "Build all binaries",
                "--example" to "Build only the specified example",
                "--examples" to "Build all examples",
                "--test" to "Build only the specified test target",
                "--tests" to "Build all tests",
                "--bench" to "Build only the specified bench target",
                "--benches" to "Build all benches",
                "--all-targets" to "Build all targets (lib and bin targets by default)",
                release,
                target,
                targetDir,
                "--out-dir" to "Copy final artifacts to this directory",
                manifestPath,
                messageFormat,
                "--build-plan" to "Output the build plan in JSON"
            ) + common + features

        private val checkOptionsDescriptions: Map<String, String> =
            hashMapOf(
                "--package" to "Package(s) to check",
                "--all" to "Check all packages in the workspace",
                "--exclude" to "Exclude packages from the check",
                jobs,
                "--lib" to "Check only this package's library",
                "--bin" to "Check only the specified binary",
                "--bins" to "Check all binaries",
                "--example" to "Check only the specified example",
                "--examples" to "Check all examples",
                "--test" to "Check only the specified test target",
                "--tests" to "Check all tests",
                "--bench" to "Check only the specified bench target",
                "--benches" to "Check all benches",
                "--all-targets" to "Check all targets",
                "--release" to "Check artifacts in release mode, with optimizations",
                "--profile" to "Profile to build the selected target for",
                "--target" to "Check for the target triple",
                targetDir,
                manifestPath,
                messageFormat
            ) + common + features

        private val cleanOptionsDescriptions: Map<String, String> =
            hashMapOf(
                "--package" to "Package to clean artifacts for",
                manifestPath,
                "--target" to "Target triple to clean output for (default all)",
                targetDir,
                "--release" to "Whether or not to clean release artifacts",
                "--doc" to " Whether or not to clean just the documentation directory"
            ) + common

        private val docOptionsDescriptions: Map<String, String> =
            hashMapOf(
                "--open" to "Opens the docs in a browser after the operation",
                "--package" to "Package to document",
                "--all" to "Document all packages in the workspace",
                exclude,
                "--no-deps" to "Don't build documentation for dependencies",
                "--document-private-items" to "Document private items",
                jobs,
                "--lib" to "Document only this package's library",
                "--bin" to "Document only the specified binary",
                "--bins" to "Document all binaries",
                release,
                target,
                targetDir,
                manifestPath,
                messageFormat
            ) + common + features

        private val runOptionsDescriptions: Map<String, String> =
            hashMapOf(
                "--bin" to "Name of the bin target to run",
                "--example" to "Name of the example target to run",
                "--package" to "Package with the target to run",
                jobs,
                release,
                target,
                targetDir,
                manifestPath,
                messageFormat
            ) + common + features

        private val testOptionsDescriptions: Map<String, String> =
            hashMapOf(
                "--lib" to "Test only this package's library",
                "--bin" to "Test only the specified binary",
                "--bins" to "Test all binaries",
                "--example" to "Test only the specified example",
                "--examples" to "Test all examples",
                "--test" to "Test only the specified test target",
                "--tests" to "Test all tests",
                "--bench" to "Test only the specified bench target",
                "--benches" to "Test all benches",
                "--all-targets" to "Test all targets",
                "--doc" to "Test only this library's documentation",
                "--no-run" to "Compile, but don't run tests",
                "--no-fail-fast" to "Run all tests regardless of failure",
                "--package" to "Package to run tests for",
                "--all" to "Test all packages in the workspace",
                "--exclude" to "Exclude packages from the test",
                jobs,
                release,
                target,
                targetDir,
                manifestPath,
                messageFormat
            ) + common + features

        private val benchOptionsDescriptions: Map<String, String> =
            hashMapOf(
                "--lib" to "Benchmark only this package's library",
                "--bin" to "Benchmark only the specified binary",
                "--bins" to "Benchmark all binaries",
                "--example" to "Benchmark only the specified example",
                "--examples" to "Benchmark all examples",
                "--test" to "Benchmark only the specified test target",
                "--tests" to "Benchmark all tests",
                "--bench" to "Benchmark only the specified bench target",
                "--benches" to "Benchmark all benches",
                "--all-targets" to "Benchmark all targets",
                "--no-run" to "Compile, but don't run benchmarks",
                "--package" to "Package to run benchmarks for",
                "--all" to "Benchmark all packages in the workspace",
                "--exclude" to "Exclude packages from the benchmark",
                jobs,
                target,
                targetDir,
                manifestPath,
                messageFormat,
                "--no-fail-fast" to "Run all benchmarks regardless of failure"
            ) + common + features

        private val updateOptionsDescriptions: Map<String, String> =
            hashMapOf(
                "--package" to "Package to update",
                "--aggressive" to "Force updating all dependencies of <name> as well",
                "--precise" to "Update a single dependency to exactly PRECISE",
                manifestPath
            ) + common

        private val searchOptionsDescriptions: Map<String, String> =
            hashMapOf(
                index,
                "--limit" to "Limit the number of results (default: 10, max: 100)",
                registry
            ) + common

        private val publishOptionsDescriptions: Map<String, String> =
            hashMapOf(
                index,
                "--token" to "Token to use when uploading",
                "--no-verify" to "Don't verify the contents by building them",
                "--allow-dirty" to "Allow dirty working directories to be packaged",
                target,
                targetDir,
                manifestPath,
                jobs,
                "--dry-run" to "Perform all checks without uploading",
                "--registry" to "Registry to publish to"
            ) + common

        private val installOptionsDescriptions: Map<String, String> =
            hashMapOf(
                "--version" to "Specify a version to install from crates.io",
                "--git" to "Git URL to install the specified crate from",
                "--branch" to "Branch to use when installing from git",
                "--tag" to "Tag to use when installing from git",
                "--rev" to "Specific commit to use when installing from git",
                "--path" to "Filesystem path to local crate to install",
                "--list" to "list all installed packages and their versions",
                jobs,
                "--force" to "Force overwriting existing crates or binaries",
                "--debug" to "Build in debug mode instead of release mode",
                "--bin" to "Install only the specified binary",
                "--bins" to "Install all binaries",
                "--example" to "Install only the specified example",
                "--examples" to "Install all examples",
                target,
                "--root" to "Directory to install packages into",
                registry
            ) + common + features

        private val uninstallOptionsDescriptions: Map<String, String> =
            hashMapOf(
                "--package" to "Package to uninstall",
                "--bin" to "Only uninstall the binary NAME",
                "--root" to "Directory to uninstall packages from"
            ) + common
    }
}
