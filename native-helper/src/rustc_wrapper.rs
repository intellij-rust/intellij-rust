use std::ffi::OsString;
use std::io;
use std::process::{Command, Stdio};

pub struct ExitCode(pub Option<i32>);

pub fn run_rustc_skipping_cargo_checking(
    rustc_executable: OsString,
    args: Vec<OsString>,
) -> io::Result<ExitCode> {
    // `CARGO_CFG_TARGET_ARCH` is only set by cargo when executing build scripts
    // We don't want to exit out checks unconditionally with success if a build
    // script tries to invoke checks themselves
    // See https://github.com/rust-lang/rust-analyzer/issues/12973 for context
    let is_invoked_by_build_script = std::env::var_os("CARGO_CFG_TARGET_ARCH").is_some();

    let is_cargo_check = args.iter().any(|arg| {
        let arg = arg.to_string_lossy();
        // `cargo check` invokes `rustc` with `--emit=metadata` argument.
        //
        // https://doc.rust-lang.org/rustc/command-line-arguments.html#--emit-specifies-the-types-of-output-files-to-generate
        // link —     Generates the crates specified by --crate-type. The default
        //            output filenames depend on the crate type and platform. This
        //            is the default if --emit is not specified.
        // metadata — Generates a file containing metadata about the crate.
        //            The default output filename is CRATE_NAME.rmeta.
        arg.starts_with("--emit=") && arg.contains("metadata") && !arg.contains("link")
    });
    if !is_invoked_by_build_script && is_cargo_check {
        return Ok(ExitCode(Some(0)));
    }
    run_rustc(rustc_executable, args)
}

fn run_rustc(rustc_executable: OsString, args: Vec<OsString>) -> io::Result<ExitCode> {
    const RUSTC_BOOTSTRAP: &'static str = "RUSTC_BOOTSTRAP";
    const ORIGINAL_RUSTC_BOOTSTRAP: &'static str = "INTELLIJ_ORIGINAL_RUSTC_BOOTSTRAP";

    let mut command = Command::new(rustc_executable);
    command
        .args(args)
        .stdin(Stdio::inherit())
        .stdout(Stdio::inherit())
        .stderr(Stdio::inherit());

    // IntelliJ Rust plugin may add `RUSTC_BOOTSTRAP=1` environment variable to `cargo check` call
    // during build script execution.
    // But we need it only to use experimental `cargo` feature
    // and don't want to propagate it to `rustc` call because it may produce different results.
    // So we use `INTELLIJ_ORIGINAL_RUSTC_BOOTSTRAP` environment variable to keep original value
    // of `RUSTC_BOOTSTRAP` and restore it (if needed) for `rustc` call.
    //
    // See https://github.com/intellij-rust/intellij-rust/issues/9700
    command.env_remove(RUSTC_BOOTSTRAP);
    if let Some(original_rustc_bootstrap_value) = std::env::var_os(ORIGINAL_RUSTC_BOOTSTRAP) {
        command.env(RUSTC_BOOTSTRAP, original_rustc_bootstrap_value);
    }
    let mut child = command
        .spawn()?;
    Ok(ExitCode(child.wait()?.code()))
}
