// Inspired by https://github.com/rust-lang/rust/blob/master/src/tools/compiletest/src/runtest.rs
#[macro_use]
extern crate serde_derive;
extern crate toml;

use std::fs::File;
use std::io::BufRead;
use std::io::BufReader;
use std::io::Write;
use std::path::Path;
use std::process::Command;
use std::process::ExitStatus;
use std::process::Output;

static ENABLE_RUST: &'static str = "type category enable Rust";
static BINARY: &'static str = "testbinary";

#[derive(Clone, Deserialize)]
pub struct LLDBConfig {
    pub test_dir: String,
    pub pretty_printers_path: String,
    pub lldb_batchmode: String,
    pub lldb_lookup: String,
    pub lldb_python: String,
    pub python: String,
    pub print_lldb_stdout: bool,
    pub lldb_native_rust: bool,
}

struct DebuggerCommands {
    commands: Vec<String>,
    check_lines: Vec<String>,
    breakpoint_lines: Vec<usize>,
}

pub struct LLDBTestRunner<'test> {
    pub config: &'test LLDBConfig,
    pub src_path: &'test Path,
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

impl<'test> LLDBTestRunner<'test> {
    pub fn run(&self) -> Result<(), String> {
        let compile_result = self.compile_test();
        if !compile_result.status.success() {
            return Err(String::from("Compilation failed!"));
        }

        let prefixes = if self.config.lldb_native_rust {
            static PREFIXES: &'static [&'static str] = &["lldb", "lldbr"];
            PREFIXES
        } else {
            static PREFIXES: &'static [&'static str] = &["lldb", "lldbg"];
            PREFIXES
        };

        // Parse debugger commands etc from test files
        let DebuggerCommands {
            commands,
            check_lines,
            breakpoint_lines,
            ..
        } = self.parse_debugger_commands(prefixes)?;

        // Write debugger script:
        // We don't want to hang when calling `quit` while the process is still running
        let mut script_str = String::from("settings set auto-confirm true\n");

        script_str.push_str(&format!("command script import {}{}.py\n", &self.config.pretty_printers_path, &self.config.lldb_lookup));
        script_str.push_str(&format!("type synthetic add -l {}.synthetic_lookup -x '.*' --category Rust\n", &self.config.lldb_lookup));
        script_str.push_str(&format!("type summary add -F {}.summary_lookup -e -x -h '.*' --category Rust\n", &self.config.lldb_lookup));
        script_str.push_str(&format!("{}\n", ENABLE_RUST));

        // Set breakpoints on every line that contains the string "#break"
        let source_file_name = self.src_path.file_name().unwrap().to_string_lossy();
        for line in &breakpoint_lines {
            script_str.push_str(&format!(
                "breakpoint set --file '{}' --line {}\n",
                source_file_name, line
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
        self.dump_output_file(&script_str, "debugger.script");
        let debugger_script = Path::new("debugger.script");
        let lldb_batchmode_path = Path::new(&self.config.lldb_batchmode);
        let exe_file = Path::new("./").join(BINARY);

        // Let LLDB execute the script via lldb_batchmode.py
        let debugger_run_result = self.run_lldb(&exe_file, &debugger_script, &lldb_batchmode_path);

        if !debugger_run_result.status.success() {
            return Err(String::from(format!("Error while running LLDB:\n{}", debugger_run_result.stderr)));
        }

        self.check_debugger_output(&debugger_run_result, &check_lines)
    }

    fn compile_test(&self) -> ProcessResult {
        let out = Command::new("rustc")
            .arg("--crate-type")
            .arg("bin")
            .arg("-o")
            .arg(BINARY)
            .arg("-g")
            .arg(&self.src_path.as_os_str())
            .output()
            .unwrap();

        ProcessResult::from(&out)
    }

    fn dump_output_file(&self, out: &str, extension: &str) {
        File::create(&extension)
            .unwrap()
            .write_all(out.as_bytes())
            .unwrap();
    }

    fn parse_debugger_commands(&self, debugger_prefixes: &[&str]) -> Result<DebuggerCommands, String> {
        let directives = debugger_prefixes
            .iter()
            .map(|prefix| (format!("{}-command", prefix), format!("{}-check", prefix)))
            .collect::<Vec<_>>();

        let mut breakpoint_lines = vec![];
        let mut commands = vec![];
        let mut check_lines = vec![];
        let mut counter = 1;
        let reader = BufReader::new(File::open(&self.src_path).unwrap());
        for line in reader.lines() {
            match line {
                Ok(line) => {
                    let line = if line.starts_with("//") {
                        line[2..].trim_left()
                    } else {
                        line.as_str()
                    };

                    if line.contains("#break") {
                        breakpoint_lines.push(counter);
                    }

                    for &(ref command_directive, ref check_directive) in &directives {
                        self.parse_name_value_directive(&line, command_directive)
                            .map(|cmd| commands.push(cmd));

                        self.parse_name_value_directive(&line, check_directive)
                            .map(|cmd| check_lines.push(cmd));
                    }
                }
                Err(e) => {
                    return Err(format!("Error while parsing debugger commands: {}", e));
                }
            }
            counter += 1;
        }

        Ok(DebuggerCommands { commands, check_lines, breakpoint_lines })
    }

    fn run_lldb(
        &self,
        test_executable: &Path,
        debugger_script: &Path,
        lldb_batchmode: &Path,
    ) -> ProcessResult {
        // Prepare the lldb_batchmode which executes the debugger script
        let out = Command::new(&self.config.python)
            .arg(lldb_batchmode)
            .arg(test_executable)
            .arg(debugger_script)
            .env("PYTHONPATH", &self.config.lldb_python)
            .output()
            .unwrap();

        ProcessResult::from(&out)
    }

    fn check_debugger_output(&self, debugger_result: &ProcessResult, check_lines: &[String]) -> Result<(), String> {
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

            started = started || line.contains(ENABLE_RUST);
        }

        if check_line_index != num_check_lines && num_check_lines > 0 {
            let mut result = String::new();
            if self.config.print_lldb_stdout {
                result.push_str("---------------- LLDB stdout ----------------\n");
                result.push_str(&output_lines.join("\n").trim());
                result.push_str("\n---------------------------------------------");
            }
            Err(format!("{}\nNot found: {}", result, check_lines[check_line_index]))
        } else {
            Ok(())
        }
    }

    pub fn parse_name_value_directive(&self, line: &str, directive: &str) -> Option<String> {
        let colon = directive.len();
        if line.starts_with(directive) && line.as_bytes().get(colon) == Some(&b':') {
            let value = line[(colon + 1)..].to_owned();
            Some(value)
        } else {
            None
        }
    }
}
