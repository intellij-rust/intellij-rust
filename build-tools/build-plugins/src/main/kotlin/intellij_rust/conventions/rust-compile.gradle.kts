package intellij_rust.conventions

import intellij_rust.internal.intellijRust
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

/**
 *
 */

plugins {
    id("intellij_rust.conventions.base")
}


val compileNativeCode by tasks.registering(Exec::class) {
    workingDir = rootDir.resolve("native-helper")
    executable = "cargo"
    // Hack to use unstable `--out-dir` option work for stable toolchain
    // https://doc.rust-lang.org/cargo/commands/cargo-build.html#output-options
    environment("RUSTC_BOOTSTRAP", "1")

    val hostPlatform = DefaultNativePlatform.host()
    val archName = when (val archName = hostPlatform.architecture.name) {
        "arm-v8", "aarch64" -> "arm64"
        else -> archName
    }
    val outDir = "${rootDir}/bin/${hostPlatform.operatingSystem.toFamilyName()}/$archName"

    outputs.dir(outDir).withPropertyName("outDir")

    args("build", "--release", "-Z", "unstable-options", "--out-dir", outDir)

    // It may be useful to disable compilation of native code.
    // For example, CI builds native code for each platform in separate tasks and puts it into `bin` dir manually
    // so there is no need to do it again.
    val compileNativeCode = intellijRust.compileNativeCode
    onlyIf { compileNativeCode.get() }
}
