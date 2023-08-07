/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.tools

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.toml.TomlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutput
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.net.HttpConfigurable
import com.intellij.util.text.SemVer
import org.jetbrains.annotations.TestOnly
import org.rust.bsp.service.BspConnectionService
import org.rust.RsBundle
import org.rust.cargo.CargoConfig
import org.rust.cargo.CargoConstants
import org.rust.cargo.CfgOptions
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.externalLinterSettings
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.CargoWorkspaceData
import org.rust.cargo.project.workspace.PackageId
import org.rust.cargo.runconfig.buildtool.CargoPatch
import org.rust.cargo.runconfig.command.CargoCommandConfiguration.Companion.findCargoPackage
import org.rust.cargo.runconfig.command.CargoCommandConfiguration.Companion.findCargoProject
import org.rust.cargo.runconfig.command.CargoCommandConfiguration.Companion.findCargoTargets
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.ExternalLinter
import org.rust.cargo.toolchain.RsToolchainBase
import org.rust.cargo.toolchain.RsToolchainBase.Companion.ORIGINAL_RUSTC_BOOTSTRAP
import org.rust.cargo.toolchain.RsToolchainBase.Companion.RUSTC_BOOTSTRAP
import org.rust.cargo.toolchain.RsToolchainBase.Companion.RUSTC_WRAPPER
import org.rust.cargo.toolchain.RustChannel
import org.rust.cargo.toolchain.impl.BuildMessages
import org.rust.cargo.toolchain.impl.CargoMetadata
import org.rust.cargo.toolchain.impl.CargoMetadata.replacePaths
import org.rust.cargo.toolchain.impl.CompilerMessage
import org.rust.cargo.toolchain.impl.RustcVersion
import org.rust.cargo.toolchain.tools.ProjectDescriptionStatus.BUILD_SCRIPT_EVALUATION_ERROR
import org.rust.cargo.toolchain.tools.ProjectDescriptionStatus.OK
import org.rust.cargo.toolchain.tools.Rustup.Companion.checkNeedInstallClippy
import org.rust.cargo.toolchain.wsl.RsWslToolchain
import org.rust.cargo.util.parseSemVer
import org.rust.ide.actions.InstallBinaryCrateAction
import org.rust.ide.experiments.RsExperiments
import org.rust.ide.notifications.showBalloon
import org.rust.lang.RsConstants.LIB_RS_FILE
import org.rust.lang.RsConstants.MAIN_RS_FILE
import org.rust.openapiext.*
import org.rust.openapiext.JsonUtils.tryParseJsonObject
import org.rust.stdext.RsResult
import org.rust.stdext.RsResult.Err
import org.rust.stdext.RsResult.Ok
import org.rust.stdext.buildList
import org.rust.stdext.unwrapOrElse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

fun RsToolchainBase.cargo(): Cargo = Cargo(this)

fun RsToolchainBase.cargoOrWrapper(cargoProjectDirectory: Path?): Cargo {
    val hasXargoToml = cargoProjectDirectory?.resolve(CargoConstants.XARGO_MANIFEST_FILE)
        ?.let { Files.isRegularFile(it) } == true
    val useWrapper = hasXargoToml && hasExecutable(Cargo.WRAPPER_NAME)
    return Cargo(this, useWrapper)
}

/**
 * A main gateway for executing cargo commands.
 *
 * This class is not aware of SDKs or projects, so you'll need to provide
 * paths yourself.
 *
 * It is impossible to guarantee that paths to the project or executables are valid,
 * because the user can always just `rm ~/.cargo/bin -rf`.
 */
class Cargo(
    toolchain: RsToolchainBase,
    useWrapper: Boolean = false
) : RustupComponent(if (useWrapper) WRAPPER_NAME else NAME, toolchain) {

    data class BinaryCrate(val name: String, val version: SemVer? = null) {
        companion object {
            // Examples:
            // - cargo-expand v1.0.16
            // - evcxr_repl v0.14.0-alpha.11
            // - wasm-pack v0.10.3 (/home/josh/dev/wasm-pack)
            private val VERSION_LINE: Regex = """(?<name>[\w-]+) v(?<version>\d+\.\d+\.\d+(-[\w.]+)?).*""".toRegex()

            fun from(line: String): BinaryCrate? {
                val result = VERSION_LINE.matchEntire(line) ?: return null
                val name = result.groups["name"]?.value ?: return null
                val rawVersion = result.groups["version"]?.value ?: return null
                return BinaryCrate(name, SemVer.parseFromText(rawVersion))
            }
        }
    }

    private fun listInstalledBinaryCrates(): List<BinaryCrate> =
        createBaseCommandLine("install", "--list")
            .execute(toolchain.executionTimeoutInMilliseconds)
            ?.stdoutLines
            ?.filterNot { it.startsWith(" ") }
            ?.mapNotNull { BinaryCrate.from(it) }
            .orEmpty()

    fun installBinaryCrate(project: Project, crateName: String) {
        val cargoProject = project.cargoProjects.allProjects.firstOrNull() ?: return
        val commandLine = CargoCommandLine.forProject(cargoProject, "install", listOf("--force", crateName))
        commandLine.run(cargoProject, "Install $crateName", saveConfiguration = false)
    }

    fun addDependency(project: Project, crateName: String, features: List<String> = emptyList()) {
        val cargoProject = project.cargoProjects.allProjects.firstOrNull() ?: return
        val args = mutableListOf(crateName)
        if (features.isNotEmpty()) {
            args.add("--features")
            args.add(features.joinToString(","))
        }
        val commandLine = CargoCommandLine.forProject(cargoProject, "add", args)
        commandLine.run(cargoProject, "Add dependency $crateName", saveConfiguration = false)
    }

    fun checkSupportForBuildCheckAllTargets(): Boolean {
        val lines = createBaseCommandLine("help", "check")
            .execute(toolchain.executionTimeoutInMilliseconds)
            ?.stdoutLines
            ?: return false
        return lines.any { it.contains(" --all-targets ") }
    }

    /**
     * Fetch all dependencies and calculate project information.
     *
     * This is a potentially long running operation which can
     * legitimately fail due to network errors or inability
     * to resolve dependencies. Hence it is mandatory to
     * pass an [owner] to correctly kill the process if it
     * runs for too long.
     */
    fun fullProjectDescription(
        owner: Project,
        projectDirectory: Path,
        buildTargets: List<String> = emptyList(),
        rustcVersion: RustcVersion?,
        listenerProvider: (CargoCallType) -> ProcessListener? = { null }
    ): RsResult<ProjectDescription, RsProcessExecutionOrDeserializationException> {
        //TODO: replace fetchBuildScriptsInfo with something more bsp specific
        val useBSP: Boolean = owner.service<BspConnectionService>().hasBspServer()
        if (useBSP) {
            return try {
                //TODO make returned status depend on bsp outcome
                Ok(ProjectDescription(fetchViaBSP(owner, projectDirectory), OK))
            } catch (e: JacksonException) {
                Err(RsDeserializationException(e))
            }
        }

        val rawData = fetchMetadata(owner, projectDirectory, buildTargets, listener = listenerProvider(CargoCallType.METADATA))
            .unwrapOrElse { return Err(it) }

        val buildScriptsInfo = if (isFeatureEnabled(RsExperiments.EVALUATE_BUILD_SCRIPTS)) {
            val listener = listenerProvider(CargoCallType.BUILD_SCRIPT_CHECK)
            fetchBuildScriptsInfo(owner, projectDirectory, rustcVersion, listener)
        } else {
            BuildMessages.DEFAULT
        }

        val (rawDataAdjusted, buildScriptsInfoAdjusted) =
            replacePathsSymlinkIfNeeded(rawData, buildScriptsInfo, projectDirectory)
        val workspaceData = CargoMetadata.clean(rawDataAdjusted, buildScriptsInfoAdjusted)
        val status = if (buildScriptsInfo.isSuccessful) OK else BUILD_SCRIPT_EVALUATION_ERROR
        return Ok(ProjectDescription(workspaceData, status))
    }

    fun fetchMetadata(
        owner: Project,
        projectDirectory: Path,
        buildTargets: List<String>,
        toolchainOverride: String? = null,
        environmentVariables: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT,
        listener: ProcessListener?,
    ): RsResult<CargoMetadata.Project, RsProcessExecutionOrDeserializationException> {
        val additionalArgs = mutableListOf("--verbose", "--format-version", "1", "--all-features")
        for (it in buildTargets) {
            // only include dependencies from the target platforms
            additionalArgs.add("--filter-platform")
            additionalArgs.add(it)
        }

        val json = CargoCommandLine(
            command = "metadata",
            projectDirectory,
            additionalArgs,
            toolchain = toolchainOverride,
            environmentVariables = environmentVariables
        ).execute(owner, listener = listener)
            .unwrapOrElse { return Err(it) }
            .stdout
            .dropWhile { it != '{' }
        return try {
            val project = JSON_MAPPER.readValue(json, CargoMetadata.Project::class.java)
                .convertPaths(toolchain::toLocalPath)
            Ok(project)
        } catch (e: JacksonException) {
            Err(RsDeserializationException(e))
        }
    }

    private fun fetchViaBSP(
        project: Project,
        projectDirectory: Path,
        buildTargets: List<String> = emptyList(),
        toolchainOverride: String? = null,
        listener: ProcessListener? = null,
    ): CargoWorkspaceData {
        val bspService = project.service<BspConnectionService>()
        return bspService.getProjectData(projectDirectory)
    }

    fun vendorDependencies(
        owner: Project,
        projectDirectory: Path,
        dstPath: Path,
        toolchainOverride: String? = null,
        environmentVariables: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT,
        listener: ProcessListener? = null
    ): RsProcessResult<Unit> {
        val additionalArgs = listOf("--respect-source-config", dstPath.toString())
        val commandLine = CargoCommandLine(
            "vendor",
            projectDirectory,
            additionalArgs,
            toolchain = toolchainOverride,
            environmentVariables = environmentVariables,
        )
        commandLine.execute(owner, listener = listener).unwrapOrElse { return Err(it) }
        return Ok(Unit)
    }

    /**
     * Execute `cargo rustc --print cfg` and parse output as [CfgOptions].
     * Available since Rust 1.52
     */
    fun getCfgOption(
        owner: Project,
        projectDirectory: Path?
    ): RsProcessResult<CfgOptions> {
        return createBaseCommandLine(
            "rustc", "-Z", "unstable-options", "--print", "cfg",
            workingDirectory = projectDirectory,
            environment = mapOf(RUSTC_BOOTSTRAP to "1")
        ).execute(owner).map { output -> CfgOptions.parse(output.stdoutLines) }
    }

    /**
     * Execute `cargo config get <path>` to and parse output as Jackson Tree ([JsonNode]).
     * Use [JsonNode.at] to get properties by path (in `/foo/bar` format)
     */
    fun getConfig(
        owner: Project,
        projectDirectory: Path
    ): RsResult<CargoConfig, RsProcessExecutionOrDeserializationException> {
        val parameters = mutableListOf("-Z", "unstable-options", "config", "get")

        val output = createBaseCommandLine(
            parameters,
            workingDirectory = projectDirectory,
            environment = mapOf(RUSTC_BOOTSTRAP to "1")
        ).execute(owner).unwrapOrElse { return Err(it) }.stdout

        val tree = try {
            TOML_MAPPER.readTree(output)
        } catch (e: JacksonException) {
            LOG.error(e)
            return Err(RsDeserializationException(e))
        }

        val env = tree.at("/env").fields().asSequence().toList().mapNotNull { field ->
            // Value can be either string or object with additional `forced` and `relative` params.
            // https://doc.rust-lang.org/cargo/reference/config.html#env
            if (field.value.isTextual) {
                field.key to CargoConfig.EnvValue(field.value.asText())
            } else if (field.value.isObject) {
                val valueParams = try {
                    TOML_MAPPER.treeToValue(field.value, CargoConfig.EnvValue::class.java)
                } catch (e: JacksonException) {
                    LOG.error(e)
                    return Err(RsDeserializationException(e))
                }
                field.key to CargoConfig.EnvValue(valueParams.value, valueParams.isForced, valueParams.isRelative)
            } else {
                null
            }
        }.toMap()

        val buildTargets = getBuildTargets(tree).map {
            // If a build target ends with `.json`, it's a custom toolchain.
            // To make it work in all cases (for example, fetching stdlib metadata for the same build target),
            // we save the corresponding path as absolute one not to depend on working directory
            if (it.endsWith(".json")) {
                projectDirectory.resolve(it).toAbsolutePath().systemIndependentPath
            } else {
                it
            }
        }
        return Ok(CargoConfig(buildTargets, env))
    }

    private fun getBuildTargets(tree: JsonNode): List<String> {
        val buildTargetNode = tree.at("/build/target")
        if (buildTargetNode.isTextual) return listOf(buildTargetNode.asText())
        if (buildTargetNode.isArray) return buildTargetNode.map { it.asText() }
        return emptyList()
    }

    private fun fetchBuildScriptsInfo(
        owner: Project,
        projectDirectory: Path,
        rustcVersion: RustcVersion?,
        listener: ProcessListener?
    ): BuildMessages {
        // `--all-targets` is needed here to compile:
        //   - build scripts even if a crate doesn't contain library or binary targets
        //   - dev dependencies during build script evaluation
        // `--keep-going` is needed here to compile as many proc macro artifacts as possible
        val additionalArgs = mutableListOf("--message-format", "json", "--workspace", "--all-targets")
        val useKeepGoing = rustcVersion != null && rustcVersion.semver >= RUST_1_62
        val envMap = mutableMapOf<String, String>()
        if (useKeepGoing) {
            additionalArgs += listOf("-Z", "unstable-options", "--keep-going")
            val originalRustcBootstrapValue = System.getenv(RUSTC_BOOTSTRAP)
            envMap += RUSTC_BOOTSTRAP to "1"
            // Actually, we need to pass `RUSTC_BOOTSTRAP=1` environment variable here only for `cargo`
            // and don't want to propagate it to `rustc` call because it may produce different results.
            // So we use `INTELLIJ_ORIGINAL_RUSTC_BOOTSTRAP` environment variable to keep original value
            // of `RUSTC_BOOTSTRAP` and restore it (if needed) for `rustc` call in native-helper binary
            //
            // See https://github.com/intellij-rust/intellij-rust/issues/9700
            if (originalRustcBootstrapValue != null) {
                envMap += ORIGINAL_RUSTC_BOOTSTRAP to originalRustcBootstrapValue
            }
        }
        val nativeHelper = RsPathManager.nativeHelper(toolchain is RsWslToolchain)
        if (nativeHelper != null && USE_BUILD_SCRIPT_WRAPPER.asBoolean()) {
            envMap += RUSTC_WRAPPER to nativeHelper.toString()
        }

        val envs = EnvironmentVariablesData.create(envMap, true)
        val commandLine = CargoCommandLine("check", projectDirectory, additionalArgs, environmentVariables = envs)

        val processResult = commandLine.execute(owner, listener = listener)
        if (processResult is Err) {
            LOG.warn("Build script evaluation failed", processResult.err)
        }
        val processOutput = processResult
            .ignoreExitCode()
            .unwrapOrElse {
                return BuildMessages.FAILED
            }

        val messages = mutableMapOf<PackageId, MutableList<CompilerMessage>>()

        for (line in processOutput.stdoutLines) {
            val jsonObject = tryParseJsonObject(line) ?: continue
            CompilerMessage.fromJson(jsonObject)
                ?.convertPaths(toolchain::toLocalPath)
                ?.let { messages.getOrPut(it.package_id) { mutableListOf() } += it }
        }
        return BuildMessages(messages, isSuccessful = processOutput.exitCode == 0)
    }

    /**
     * If project directory is 'source' which is a symlink to 'target' directory,
     * then `cargo metadata` command inside 'source' directory produces output with 'target' paths.
     * This function replaces paths to 'source'.
     * 'source' paths are better because files inside 'source' are indexed, and inside 'target' are not.
     */
    private fun replacePathsSymlinkIfNeeded(
        project: CargoMetadata.Project,
        buildMessages: BuildMessages?,
        projectDirectoryRel: Path
    ): Pair<CargoMetadata.Project, BuildMessages?> {
        val projectDirectory = projectDirectoryRel.toAbsolutePath()
        val workspaceRoot = project.workspace_root

        if (projectDirectory.toString() == workspaceRoot) {
            return Pair(project, buildMessages)
        }

        val workspaceRootPath = Paths.get(workspaceRoot)

        // If the selected projectDirectory doesn't resolve directly to the directory that Cargo spat out at us,
        // then there's something a bit special with the cargo workspace, and we don't want to assume anything.
        if (!Files.isSameFile(projectDirectory, workspaceRootPath)) {
            return Pair(project, buildMessages)
        }

        // Otherwise, it's just a normal symlink.
        val normalisedWorkspace = projectDirectory.normalize().toString()
        val replacer: (String) -> String = replacer@{
            if (!it.startsWith(workspaceRoot)) return@replacer it
            normalisedWorkspace + it.removePrefix(workspaceRoot)
        }
        return Pair(project.replacePaths(replacer), buildMessages?.replacePaths(replacer))
    }

    fun init(
        project: Project,
        owner: Disposable,
        directory: VirtualFile,
        name: String,
        createBinary: Boolean,
        vcs: String? = null
    ): RsProcessResult<GeneratedFilesHolder> {
        val path = directory.pathAsPath
        val crateType = if (createBinary) "--bin" else "--lib"

        val args = mutableListOf(crateType, "--name", name)

        vcs?.let {
            args.addAll(listOf("--vcs", vcs))
        }

        args.add(path.toString())

        CargoCommandLine("init", path, args).execute(project, owner).unwrapOrElse { return Err(it) }
        fullyRefreshDirectory(directory)

        val manifest = checkNotNull(directory.findChild(CargoConstants.MANIFEST_FILE)) { "Can't find the manifest file" }
        val fileName = if (createBinary) MAIN_RS_FILE else LIB_RS_FILE
        val sourceFiles = listOfNotNull(directory.findFileByRelativePath("src/$fileName"))
        return Ok(GeneratedFilesHolder(manifest, sourceFiles))
    }

    fun generate(
        project: Project,
        owner: Disposable,
        directory: VirtualFile,
        name: String,
        templateUrl: String,
        vcs: String? = null
    ): RsProcessResult<GeneratedFilesHolder> {
        val path = directory.pathAsPath
        val args = mutableListOf(
            "--name", name,
            "--git", templateUrl,
            "--init", // generate in current directory
            "--force" // enforce cargo-generate not to do underscores to hyphens name conversion
        )

        vcs?.let {
            args.addAll(listOf("--vcs", vcs))
        }

        CargoCommandLine("generate", path, args)
            .execute(project, owner)
            .unwrapOrElse { return Err(it) }
        fullyRefreshDirectory(directory)

        val manifest = checkNotNull(directory.findChild(CargoConstants.MANIFEST_FILE)) { "Can't find the manifest file" }
        val sourceFiles = listOf("main", "lib").mapNotNull { directory.findFileByRelativePath("src/${it}.rs") }
        return Ok(GeneratedFilesHolder(manifest, sourceFiles))
    }

    fun checkProject(
        project: Project,
        owner: Disposable,
        args: CargoCheckArgs
    ): RsResult<ProcessOutput, RsProcessExecutionException.Start> {
        val useClippy = args.linter == ExternalLinter.CLIPPY
            && !checkNeedInstallClippy(project, args.cargoProjectDirectory)
        val checkCommand = if (useClippy) "clippy" else "check"

        val commandLine = when (args) {
            is CargoCheckArgs.SpecificTarget -> {
                val arguments = buildList<String> {
                    add("--message-format=json")
                    add("--no-default-features")
                    val enabledFeatures = args.target.pkg.featureState.filterValues { it.isEnabled }.keys.toList()
                    if (enabledFeatures.isNotEmpty()) {
                        add("--features")
                        add(enabledFeatures.joinToString(separator = " "))
                    }
                    if (args.target.kind !is CargoWorkspace.TargetKind.Test) {
                        // Check `#[test]`/`#[cfg(test)]`
                        // TODO try using `--profile test`, see https://github.com/intellij-rust/intellij-rust/issues/6277
                        add("--tests")
                    }
                    addAll(ParametersListUtil.parse(args.extraArguments))
                }
                CargoCommandLine.forTarget(
                    args.target,
                    checkCommand,
                    arguments,
                    args.channel,
                    EnvironmentVariablesData.create(args.envs, true),
                    usePackageOption = false
                )
            }
            is CargoCheckArgs.FullWorkspace -> {
                val arguments = buildList<String> {
                    add("--message-format=json")
                    add("--all")
                    if (args.allTargets && checkSupportForBuildCheckAllTargets()) {
                        add("--all-targets")
                    }
                    addAll(ParametersListUtil.parse(args.extraArguments))
                }
                CargoCommandLine(
                    checkCommand,
                    args.cargoProjectDirectory,
                    arguments,
                    channel = args.channel,
                    environmentVariables = EnvironmentVariablesData.create(args.envs, true)
                )
            }
        }

        return commandLine.execute(project, owner).ignoreExitCode()
    }

    fun toColoredCommandLine(project: Project, commandLine: CargoCommandLine): GeneralCommandLine =
        toGeneralCommandLine(project, commandLine, colors = true)

    fun toGeneralCommandLine(project: Project, commandLine: CargoCommandLine): GeneralCommandLine =
        toGeneralCommandLine(project, commandLine, colors = false)

    private fun toGeneralCommandLine(project: Project, commandLine: CargoCommandLine, colors: Boolean): GeneralCommandLine =
        with(commandLine.patchArgs(project, colors)) {
            val parameters = buildList {
                when {
                    channel != RustChannel.DEFAULT -> add("+$channel")
                    toolchain != null -> add("+$toolchain")
                }
                if (project.rustSettings.useOffline) {
                    val cargoProject = findCargoProject(project, additionalArguments, workingDirectory)
                    val rustcVersion = cargoProject?.rustcInfo?.version?.semver
                    if (rustcVersion != null) {
                        add("--offline")
                    }
                }
                add(command)
                addAll(additionalArguments)
            }
            val rustcExecutable = this@Cargo.toolchain.rustc().executable.toString()
            this@Cargo.toolchain.createGeneralCommandLine(
                executable,
                workingDirectory,
                redirectInputFrom,
                backtraceMode,
                environmentVariables,
                parameters,
                emulateTerminal,
                // TODO: always pass `withSudo` when `com.intellij.execution.process.ElevationService` supports error stream redirection
                // https://github.com/intellij-rust/intellij-rust/issues/7320
                if (isFeatureEnabled(RsExperiments.BUILD_TOOL_WINDOW)) withSudo else false,
                http = http
            ).withEnvironment("RUSTC", rustcExecutable)
        }

    private fun CargoCommandLine.execute(
        project: Project,
        owner: Disposable = project,
        stdIn: ByteArray? = null,
        listener: ProcessListener? = null
    ): RsProcessResult<ProcessOutput> {
        return toGeneralCommandLine(project, copy(emulateTerminal = false)).execute(owner, stdIn, listener = listener)
    }

    fun installCargoGenerate(owner: Disposable, listener: ProcessListener): RsResult<Unit, RsProcessExecutionException.Start> {
        return createBaseCommandLine("install", "cargo-generate")
            .execute(owner, listener = listener)
            .ignoreExitCode()
            .map { }
    }

    fun checkNeedInstallCargoGenerate(): Boolean {
        val crateName = "cargo-generate"
        val minVersion = "0.9.0".parseSemVer()
        return checkBinaryCrateIsNotInstalled(crateName, minVersion)
    }

    private fun checkBinaryCrateIsNotInstalled(crateName: String, minVersion: SemVer?): Boolean {
        val installed = listInstalledBinaryCrates().any { (name, version) ->
            name == crateName && (minVersion == null || version != null && version >= minVersion)
        }
        return !installed
    }

    private var _http: HttpConfigurable? = null
    private val http: HttpConfigurable
        get() = _http ?: HttpConfigurable.getInstance()

    @TestOnly
    fun setHttp(http: HttpConfigurable) {
        _http = http
    }

    companion object {
        private val LOG: Logger = logger<Cargo>()

        private val JSON_MAPPER: ObjectMapper = ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerKotlinModule()
        private val TOML_MAPPER = TomlMapper()

        @JvmStatic
        val TEST_NOCAPTURE_ENABLED_KEY: RegistryValue = Registry.get("org.rust.cargo.test.nocapture")

        val USE_BUILD_SCRIPT_WRAPPER: RegistryValue = Registry.get("org.rust.cargo.evaluate.build.scripts.wrapper")

        const val NAME: String = "cargo"
        const val WRAPPER_NAME: String = "xargo"

        private val FEATURES_ACCEPTING_COMMANDS: List<String> = listOf(
            "bench", "build", "check", "doc", "fix", "run", "rustc", "rustdoc", "test", "metadata", "tree", "install", "package", "publish"
        )

        private val COLOR_ACCEPTING_COMMANDS: List<String> = listOf(
            "bench", "build", "check", "clean", "clippy", "doc", "install", "publish", "run", "rustc", "test", "update"
        )

        data class GeneratedFilesHolder(val manifest: VirtualFile, val sourceFiles: List<VirtualFile>)

        fun getCargoCommonPatch(project: Project): CargoPatch = { it.patchArgs(project, true) }

        fun CargoCommandLine.patchArgs(project: Project, colors: Boolean): CargoCommandLine {
            val (pre, post) = splitOnDoubleDash()
                .let { (pre, post) -> pre.toMutableList() to post.toMutableList() }

            if (command in listOf("test", "bench")) {
                if (allFeatures && !pre.contains("--all-features")) {
                    pre.add("--all-features")
                }
                if (TEST_NOCAPTURE_ENABLED_KEY.asBoolean() && !post.contains("--nocapture")) {
                    post.add(0, "--nocapture")
                }
            }

            if (requiredFeatures && command in FEATURES_ACCEPTING_COMMANDS) {
                run {
                    val cargoProject = findCargoProject(project, additionalArguments, workingDirectory) ?: return@run
                    val cargoPackage = findCargoPackage(cargoProject, additionalArguments, workingDirectory)
                        ?: return@run
                    if (workingDirectory != cargoPackage.rootDirectory) {
                        val manifestIdx = pre.indexOf("--manifest-path")
                        val packageIdx = pre.indexOf("--package")
                        if (manifestIdx == -1 && packageIdx != -1) {
                            pre.removeAt(packageIdx) // remove `--package`
                            pre.removeAt(packageIdx) // remove package name
                            pre.add("--manifest-path")
                            val manifest = cargoPackage.rootDirectory.resolve(CargoConstants.MANIFEST_FILE)
                            pre.add(manifest.toAbsolutePath().toString())
                        }
                    }
                    val cargoTargets = findCargoTargets(cargoPackage, additionalArguments)
                    val features = cargoTargets.flatMap { it.requiredFeatures }.distinct().joinToString(",")
                    if (features.isNotEmpty()) pre.add("--features=$features")
                }
            }

            // Force colors
            val forceColors = colors &&
                command in COLOR_ACCEPTING_COMMANDS &&
                additionalArguments.none { it.startsWith("--color") }
            if (forceColors) pre.add(0, "--color=always")

            return copy(additionalArguments = if (post.isEmpty()) pre else pre + "--" + post)
        }

        fun checkNeedInstallGrcov(project: Project): Boolean {
            val crateName = RsBundle.message("notification.content.grcov")
            val minVersion = "0.7.0".parseSemVer()
            return checkNeedInstallBinaryCrate(
                project,
                crateName,
                NotificationType.ERROR,
                RsBundle.message("notification.content.need.at.least4", crateName, minVersion),
                minVersion
            )
        }

        fun checkNeedInstallCargoExpand(project: Project): Boolean {
            val crateName = RsBundle.message("notification.content.cargo.expand")
            val minVersion = "1.0.0".parseSemVer()
            return checkNeedInstallBinaryCrate(
                project,
                crateName,
                NotificationType.ERROR,
                RsBundle.message("notification.content.need.at.least3", crateName, minVersion),
                minVersion
            )
        }

        fun checkNeedInstallEvcxr(project: Project): Boolean {
            val crateName = "evcxr_repl"
            val minVersion = "0.14.2".parseSemVer()
            return checkNeedInstallBinaryCrate(
                project,
                crateName,
                NotificationType.ERROR,
                RsBundle.message("notification.content.need.at.least2", crateName, minVersion),
                minVersion
            )
        }

        fun checkNeedInstallWasmPack(project: Project): Boolean {
            val crateName = RsBundle.message("notification.content.wasm.pack")
            val minVersion = "0.9.1".parseSemVer()
            return checkNeedInstallBinaryCrate(
                project,
                crateName,
                NotificationType.ERROR,
                RsBundle.message("notification.content.need.at.least", crateName, minVersion),
                minVersion
            )
        }

        private fun checkNeedInstallBinaryCrate(
            project: Project,
            crateName: String,
            notificationType: NotificationType,
            @NlsContexts.NotificationContent message: String? = null,
            minVersion: SemVer? = null
        ): Boolean {
            val cargo = project.toolchain?.cargo() ?: return false
            val isNotInstalled = { cargo.checkBinaryCrateIsNotInstalled(crateName, minVersion) }
            val needInstall = if (isDispatchThread) {
                project.computeWithCancelableProgress(RsBundle.message("progress.title.checking.if.installed", crateName), isNotInstalled)
            } else {
                isNotInstalled()
            }

            if (needInstall) {
                project.showBalloon(
                    RsBundle.message("notification.title.code.code.not.installed", crateName),
                    message ?: "",
                    notificationType,
                    InstallBinaryCrateAction(crateName)
                )
            }

            return needInstall
        }
    }
}

// Note: the class is used as a cache key, so it must be immutable and must have a correct equals/hashCode
sealed class CargoCheckArgs {
    abstract val linter: ExternalLinter
    abstract val cargoProjectDirectory: Path
    abstract val extraArguments: String
    abstract val channel: RustChannel
    abstract val envs: Map<String, String>

    data class SpecificTarget(
        override val linter: ExternalLinter,
        override val cargoProjectDirectory: Path,
        val target: CargoWorkspace.Target,
        override val extraArguments: String,
        override val channel: RustChannel,
        override val envs: Map<String, String>
    ) : CargoCheckArgs()

    data class FullWorkspace(
        override val linter: ExternalLinter,
        override val cargoProjectDirectory: Path,
        val allTargets: Boolean,
        override val extraArguments: String,
        override val channel: RustChannel,
        override val envs: Map<String, String>
    ) : CargoCheckArgs()

    companion object {
        fun forTarget(project: Project, target: CargoWorkspace.Target): CargoCheckArgs {
            val settings = project.externalLinterSettings
            return SpecificTarget(
                settings.tool,
                target.pkg.workspace.contentRoot,
                target,
                settings.additionalArguments,
                settings.channel,
                settings.envs
            )
        }

        fun forCargoProject(cargoProject: CargoProject): CargoCheckArgs {
            val settings = cargoProject.project.externalLinterSettings
            return FullWorkspace(
                settings.tool,
                cargoProject.workingDirectory,
                cargoProject.project.rustSettings.compileAllTargets,
                settings.additionalArguments,
                settings.channel,
                settings.envs
            )
        }
    }
}

enum class CargoCallType {
    METADATA,
    BUILD_SCRIPT_CHECK
}

data class ProjectDescription(
    val workspaceData: CargoWorkspaceData,
    val status: ProjectDescriptionStatus
)

enum class ProjectDescriptionStatus {
    BUILD_SCRIPT_EVALUATION_ERROR,
    OK
}

private val RUST_1_62: SemVer = "1.62.0".parseSemVer()
