use std::fs;

use serde::Deserialize;

use test_runner::{compile_tests, Config, TestResult};
use test_runner::create_test_runner;
use test_runner::Debugger;
use test_runner::GDBConfig;
use test_runner::LLDBConfig;
use test_runner::TestRunner;

#[cfg(target_os = "linux")]
const SETTINGS: &str = "Settings_linux.toml";
#[cfg(target_os = "macos")]
const SETTINGS: &str = "Settings_macos.toml";
#[cfg(target_os = "windows")]
const SETTINGS: &str = "Settings_windows.toml";
#[cfg(all(not(target_os = "linux"), not(target_os = "macos"), not(target_os = "windows")))]
panic!("Unsupported platform");

#[derive(Deserialize)]
struct Settings {
    pretty_printers_path: String,
    print_stdout: bool,
    lldb: Option<LLDBSettings>,
}

#[derive(Deserialize)]
struct LLDBSettings {
    python: String,
    lldb_batchmode: String,
}

/// Expects "lldb" or "gdb as the first argument
fn main() -> Result<(), ()> {
    let args: Vec<String> = std::env::args().collect();
    let debugger = args.get(1).expect("You need to choose a debugger (lldb or gdb)").clone().to_lowercase();
    let debugger_path = args.get(2);

    if debugger == "lldb" {
        let lldb_python = debugger_path.unwrap_or(&String::from("./")).clone();
        test(Debugger::LLDB, lldb_python)
    } else if debugger == "gdb" {
        let gdb_binary = args.get(2).unwrap_or(&String::from("gdb")).clone();
        test(Debugger::GDB, gdb_binary)
    } else {
        panic!("Invalid debugger");
    }
}

fn test(debugger: Debugger, path: String) -> Result<(), ()> {
    let settings = fs::read_to_string(SETTINGS).expect(&format!("Cannot read {}", SETTINGS));
    let settings: Settings = toml::from_str(&settings).expect(&format!("Invalid {}", SETTINGS));

    let config = match debugger {
        Debugger::LLDB => {
            let lldb_settings = settings.lldb.expect(&format!("No LLDB settings in {}", SETTINGS));
            Config::LLDB(LLDBConfig {
                pretty_printers_path: settings.pretty_printers_path,
                print_stdout: settings.print_stdout,
                lldb_python: path,
                lldb_batchmode: lldb_settings.lldb_batchmode,
                python: lldb_settings.python,
            })
        },

        Debugger::GDB => {
            Config::GDB(GDBConfig {
                pretty_printers_path: settings.pretty_printers_path,
                print_stdout: settings.print_stdout,
                gdb: path,
            })
        }
    };

    let mut status = Ok(());
    let tests = compile_tests().unwrap_or_else(|e| {
        println!("{}", e);
        status = Err(());
        vec![]
    });

    for test in tests {
        let test_name = &test.name;
        let test_runner: Box<dyn TestRunner> = create_test_runner(&config, &test);
        let result = test_runner.run();

        match result {
            TestResult::Ok => {
                println!("{}: passed", test_name);
            },
            TestResult::Skipped(reason) => {
                println!("{}: skipped", test_name);
                println!("{}", reason);
            },
            TestResult::Err(reason) => {
                println!("{}: failed", test_name);
                println!("{}", reason);
                status = Err(());
            },
        }
    }

    status
}
