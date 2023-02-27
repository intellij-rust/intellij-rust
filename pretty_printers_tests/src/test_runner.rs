// Inspired by https://github.com/rust-lang/rust/blob/master/src/tools/compiletest/src/runtest.rs

use std::ffi::{OsStr, OsString};
use std::fs::File;
use std::io::BufRead;
use std::io::BufReader;
use std::io::Write;
use std::path::Path;
use std::process::Command;
use std::process::ExitStatus;
use std::process::Output;

use rustc_version_runtime::Version;
use serde::Deserialize;

static ENABLE_RUST: &'static str = "type category enable Rust";

#[derive(Clone)]
pub struct LLDBConfig {
    pub pretty_printers_path: String,
    pub print_stdout: bool,
    pub lldb_python: String,
    pub lldb_batchmode: String,
    pub python: String,
}

#[derive(Clone)]
pub struct GDBConfig {
    pub pretty_printers_path: String,
    pub print_stdout: bool,
    pub gdb: String,
}

#[derive(Clone)]
pub enum Config { LLDB(LLDBConfig), GDB(GDBConfig) }

enum DebuggerCommands {
    Run {
        commands: Vec<String>,
        check_lines: Vec<String>,
        breakpoint_lines: Vec<usize>,
    },
    Skip(String),
    Err(String),
}

#[derive(Deserialize)]
struct Target {
    pub kind: Vec<String>,
    pub crate_types: Vec<String>,
    pub name: String,
    pub src_path: String,
}

#[derive(Deserialize)]
struct CompilerArtifact {
    pub target: Target,
    pub executable: Option<String>,
}

pub struct TestArtifact {
    pub name: String,
    pub src_path: String,
    pub executable: String,
}

pub trait TestRunner<'test> {
    fn run(&self) -> TestResult;
}

pub enum TestResult {
    Ok,
    Skipped(String),
    Err(String),
}

pub fn create_test_runner<'test>(
    config: &'test Config,
    test: &'test TestArtifact,
) -> Box<dyn TestRunner<'test> + 'test> {
    match config {
        Config::LLDB(config) => Box::new(LLDBTestRunner { config, test }),
        Config::GDB(config) => Box::new(GDBTestRunner { config, test })
    }
}

pub struct LLDBTestRunner<'test> {
    pub config: &'test LLDBConfig,
    pub test: &'test TestArtifact,
}

pub struct GDBTestRunner<'test> {
    pub config: &'test GDBConfig,
    pub test: &'test TestArtifact,
}

struct ProcessResult {
    status: ExitStatus,
    stdout: String,
    stderr: String,
}

impl ProcessResult {
    fn from(out: &Output) -> ProcessResult {
        ProcessResult {
            status: out.status,
            stdout: String::from_utf8_lossy(&out.stdout).into_owned(),
            stderr: String::from_utf8_lossy(&out.stderr).into_owned(),
        }
    }
}

impl<'test> GDBTestRunner<'test> {
    fn run_gdb<T: AsRef<OsStr>>(
        &self,
        test_executable: T,
        debugger_opts: &[&OsStr],
    ) -> ProcessResult {
        let out = Command::new(&self.config.gdb)
            .arg(test_executable)
            .args(debugger_opts).output()
            .unwrap();

        ProcessResult::from(&out)
    }
}

impl<'test> TestRunner<'test> for GDBTestRunner<'test> {
    fn run(&self) -> TestResult {
        let prefixes: &'static [&'static str] = &["gdb"];

        // Parse debugger commands etc from test files
        let (commands, check_lines, breakpoint_lines) = match parse_debugger_commands(&self.test.src_path, prefixes) {
            DebuggerCommands::Run {
                commands,
                check_lines,
                breakpoint_lines,
            } => (commands, check_lines, breakpoint_lines),
            DebuggerCommands::Skip(reason) => return TestResult::Skipped(reason),
            DebuggerCommands::Err(reason) => return TestResult::Err(reason),
        };

        let mut script_str = String::with_capacity(2048);
        // script_str.push_str(&format!("set charset {}\n", charset));
        script_str.push_str("show version\n");

        // The following line actually doesn't have to do anything with
        // pretty printing, it just tells GDB to print values on one line:
        script_str.push_str("set print pretty off\n");

        // Add the pretty printer directory to GDB's source-file search path
        script_str.push_str(&format!("directory {}\n", self.config.pretty_printers_path));

        script_str.push_str(&format!("python sys.path.insert(0, \"{}\")\n", self.config.pretty_printers_path));
        script_str.push_str("python import gdb_formatters.gdb_lookup\n");
        script_str.push_str("python gdb_formatters.gdb_lookup.register_printers(gdb)\n");

        // Load the target executable
        script_str.push_str(&format!("file {}\n", &self.test.executable));

        // Force GDB to print values in the Rust format.
        script_str.push_str("set language rust\n");

        // Add line breakpoints
        for line in &breakpoint_lines {
            script_str.push_str(&format!("break '{}':{}\n", &self.test.src_path, line));
        }

        for line in &commands {
            script_str.push_str(line);
            script_str.push_str("\n");
        }
        script_str.push_str("\nquit\n");

        dump_output_file(&script_str, "debugger.script");

        let debugger_script = OsString::from("-command=debugger.script");
        let debugger_opts: &[&OsStr] = &[
            "-quiet".as_ref(),
            "-batch".as_ref(),
            "-nx".as_ref(),
            &debugger_script,
        ];

        let debugger_run_result = self.run_gdb(&self.test.executable, &debugger_opts);

        if !debugger_run_result.status.success() {
            return TestResult::Err(String::from(format!("Error while running GDB:\n{}", debugger_run_result.stderr)));
        }

        check_debugger_output(&debugger_run_result, &check_lines, self.config.print_stdout)
    }
}

impl<'test> LLDBTestRunner<'test> {
    fn run_lldb<T: AsRef<OsStr>>(
        &self,
        test_executable: T,
        debugger_script: &Path,
        lldb_batchmode: &Path,
    ) -> ProcessResult {
        // Prepare the lldb_batchmode which executes the debugger script
        let out = Command::new(&self.config.python)
            .arg(lldb_batchmode)
            .arg(test_executable)
            .arg(debugger_script)
            .env("PYTHONPATH", &self.config.lldb_python)
            // Use UTF-8 to properly decode test output in `lldb_batchmode.py` on Windows
            .env("PYTHONIOENCODING", "utf8")
            .output()
            .unwrap();

        ProcessResult::from(&out)
    }
}

impl<'test> TestRunner<'test> for LLDBTestRunner<'test> {
    fn run(&self) -> TestResult {
        // Test runner can run commands depending on the target's platform
        let prefixes = if cfg!(unix) {
            static PREFIXES: &'static [&'static str] = &["lldb", "lldb-unix"];
            PREFIXES
        } else if cfg!(windows) {
            static PREFIXES: &'static [&'static str] = &["lldb", "lldb-windows"];
            PREFIXES
        } else {
            panic!("Unsupported platform");
        };

        // Parse debugger commands etc from test files
        let (commands, check_lines, breakpoint_lines) = match parse_debugger_commands(&self.test.src_path, prefixes) {
            DebuggerCommands::Run {
                commands,
                check_lines,
                breakpoint_lines,
            } => (commands, check_lines, breakpoint_lines),
            DebuggerCommands::Skip(reason) => return TestResult::Skipped(reason),
            DebuggerCommands::Err(reason) => return TestResult::Err(reason),
        };

        // Write debugger script:
        // We don't want to hang when calling `quit` while the process is still running
        let mut script_str = String::from("settings set auto-confirm true\n");

        script_str.push_str(&format!("command script import {}/lldb_formatters\n", &self.config.pretty_printers_path));
        script_str.push_str(&format!("{}\n", ENABLE_RUST));

        // Set breakpoints on every line that contains the string "#break"
        for line in &breakpoint_lines {
            script_str.push_str(&format!(
                "breakpoint set --file '{}' --line {}\n",
                &self.test.src_path, line
            ));
        }

        // Append the other commands
        for line in &commands {
            script_str.push_str(line);
            script_str.push_str("\n");
        }

        // Finally, quit the debugger
        script_str.push_str("\nquit\n");

        // Write the script into a file
        dump_output_file(&script_str, "debugger.script");
        let debugger_script = Path::new("debugger.script");
        let lldb_batchmode_path = Path::new(&self.config.lldb_batchmode);

        // Let LLDB execute the script via lldb_batchmode.py
        let debugger_run_result = self.run_lldb(&self.test.executable, &debugger_script, &lldb_batchmode_path);

        if !debugger_run_result.status.success() {
            return TestResult::Err(String::from(format!("Error while running LLDB:\n{}", debugger_run_result.stderr)));
        }

        check_debugger_output(
            &debugger_run_result,
            &check_lines,
            self.config.print_stdout,
        )
    }
}

fn parse_debugger_commands<T: AsRef<Path>>(src_path: T, debugger_prefixes: &[&str]) -> DebuggerCommands {
    let directives = debugger_prefixes
        .iter()
        .map(|prefix|
            (format!("{}-command", prefix), format!("{}-check", prefix))
        ).collect::<Vec<_>>();

    let rustc_version = rustc_version_runtime::version();

    let mut breakpoint_lines = vec![];
    let mut commands = vec![];
    let mut check_lines = vec![];
    let mut counter = 1;
    let reader = BufReader::new(File::open(src_path).unwrap());
    for line in reader.lines() {
        match line {
            Ok(line) => {
                let line = if line.starts_with("//") {
                    line[2..].trim_start()
                } else {
                    line.as_str()
                };

                match parse_rustc_version(line, "min-version:") {
                    Ok(Some(min_version)) => {
                        if rustc_version < min_version {
                            return DebuggerCommands::Skip(
                                format!("Current rustc version ({}) is less than minimum version ({}) required for test", rustc_version, min_version)
                            );
                        }
                    }
                    Err(e) => {
                        return DebuggerCommands::Err(format!("Error while parsing rustc version: {}", e));
                    }
                    _ => {}
                }

                match parse_rustc_version(line, "max-version:") {
                    Ok(Some(max_version)) => {
                        if rustc_version > max_version {
                            return DebuggerCommands::Skip(
                                format!("Current rustc version ({}) is greater than maximum version ({}) required for test", rustc_version, max_version)
                            );
                        }
                    }
                    Err(e) => {
                        return DebuggerCommands::Err(format!("Error while parsing rustc version: {}", e));
                    }
                    _ => {}
                }

                if line.contains("#break") {
                    breakpoint_lines.push(counter);
                }

                for &(ref command_directive, ref check_directive) in &directives {
                    parse_name_value_directive(&line, command_directive)
                        .map(|cmd| commands.push(cmd));

                    parse_name_value_directive(&line, check_directive)
                        .map(|cmd| check_lines.push(cmd));
                }
            }
            Err(e) => {
                return DebuggerCommands::Err(format!("Error while parsing debugger commands: {}", e));
            }
        }
        counter += 1;
    }
    DebuggerCommands::Run { commands, check_lines, breakpoint_lines }
}

fn parse_rustc_version(line: &str, prefix: &str) -> Result<Option<Version>, String> {
    if line.starts_with(prefix) {
        let min_version_str = &line[prefix.len()..].trim_start();
        let version = Version::parse(min_version_str).map_err(|e| e.to_string())?;
        Ok(Some(version))
    } else {
        Ok(None)
    }
}

pub fn parse_name_value_directive(line: &str, directive: &str) -> Option<String> {
    let colon = directive.len();
    if line.starts_with(directive) && line.as_bytes().get(colon) == Some(&b':') {
        let value = line[(colon + 1)..].to_owned();
        Some(value)
    } else {
        None
    }
}

fn dump_output_file(out: &str, extension: &str) {
    File::create(&extension)
        .unwrap()
        .write_all(out.as_bytes())
        .unwrap();
}

pub fn compile_tests() -> Result<Vec<TestArtifact>, String> {
    let out = Command::new("cargo")
        .arg("build")
        .arg("--examples")
        .arg("--message-format")
        .arg("json")
        .output()
        .map_err(|e| e.to_string())?;

    let result = ProcessResult::from(&out);
    if !result.status.success() {
        return Err(String::from("Compilation failed!"))
    }

    fn contains(v: &Vec<String>, element: &str) -> bool {
        v.iter().any(|s| s == element)
    }

    let examples_artifacts: Vec<TestArtifact> = result.stdout.lines()
        .filter(|line| line.contains("\"reason\":\"compiler-artifact\""))
        .filter_map(|line| serde_json::from_str::<CompilerArtifact>(line).ok())
        .filter_map(|artifact| {
            let target = artifact.target;
            match artifact.executable {
                Some(executable) if contains(&target.crate_types, "bin") && contains(&target.kind, "example") => {
                    Some(TestArtifact {
                        name: target.name,
                        src_path: target.src_path,
                        executable,
                    })
                }
                _ => None
            }
        })
        .collect();

    Ok(examples_artifacts)
}

fn check_debugger_output(
    debugger_result: &ProcessResult,
    check_lines: &[String],
    print_stdout: bool,
) -> TestResult {
    fn check_single_line(line: &str, check_line: &str) -> bool {
        // Allow check lines to leave parts unspecified (e.g., uninitialized
        // bits in the  wrong case of an enum) with the notation "[...]".
        let line = line.trim();
        let check_line = check_line.trim();
        let can_start_anywhere = check_line.starts_with("[...]");
        let can_end_anywhere = check_line.ends_with("[...]");

        let check_fragments: Vec<&str> = check_line
            .split("[...]")
            .filter(|frag| !frag.is_empty())
            .collect();
        if check_fragments.is_empty() {
            return true;
        }

        let (mut rest, first_fragment) = if can_start_anywhere {
            match line.find(check_fragments[0]) {
                Some(pos) => (&line[pos + check_fragments[0].len()..], 1),
                None => return false,
            }
        } else {
            (line, 0)
        };

        for current_fragment in &check_fragments[first_fragment..] {
            match rest.find(current_fragment) {
                Some(pos) => {
                    rest = &rest[pos + current_fragment.len()..];
                }
                None => return false,
            }
        }

        if !can_end_anywhere && !rest.is_empty() {
            return false;
        }

        true
    }

    let num_check_lines = check_lines.len();

    let mut check_line_index = 0;
    let mut started = false;
    let mut output_lines = Vec::new();

    for line in debugger_result.stdout.lines() {
        if started {
            output_lines.push(line);

            if check_line_index >= num_check_lines {
                break;
            }

            if check_single_line(line, &(check_lines[check_line_index])[..]) {
                check_line_index += 1;
            }
        }

        started = started || line.contains(ENABLE_RUST) || line.contains("Breakpoint");
    }

    if check_line_index != num_check_lines && num_check_lines > 0 {
        let mut result = String::new();
        if print_stdout {
            result.push_str("---------------- stdout ----------------\n");
            result.push_str(&output_lines.join("\n").trim());
            result.push_str("\n---------------------------------------------");
        }
        TestResult::Err(format!("{}\nNot found: {}", result, check_lines[check_line_index]))
    } else {
        TestResult::Ok
    }
}
