use std::fs;

use serde::Deserialize;

use test_runner::{compile_tests, Config, TestResult};
use test_runner::create_test_runner;
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
    lldb_batchmode: String,
}

/// Expects "lldb" or "gdb as the first argument
fn main() -> Result<(), ()> {
    let args: Vec<String> = std::env::args().collect();
    let debugger: &str = args.get(1).expect("You need to choose a debugger (lldb or gdb)");

    let settings_content = fs::read_to_string(SETTINGS).expect(&format!("Cannot read {}", SETTINGS));
    let settings: Settings = toml::from_str(&settings_content).expect(&format!("Invalid {}", SETTINGS));

    let config = match debugger {
        "lldb" => {
            let lldb_settings = settings.lldb.expect(&format!("No LLDB settings in {}", SETTINGS));
            let python = args.get(2).unwrap_or(&String::from("python3")).clone();
            let lldb_python = args.get(3).unwrap_or(&String::from("./")).clone();

            Config::LLDB(LLDBConfig {
                pretty_printers_path: settings.pretty_printers_path,
                print_stdout: settings.print_stdout,
                lldb_python,
                lldb_batchmode: lldb_settings.lldb_batchmode,
                python,
            })
        }
        "gdb" => {
            let gdb = args.get(2).unwrap_or(&String::from("gdb")).clone();
            Config::GDB(GDBConfig {
                pretty_printers_path: settings.pretty_printers_path,
                print_stdout: settings.print_stdout,
                gdb,
            })
        }
        _ => panic!("Invalid debugger")
    };
    test(&config)
}

fn test(config: &Config) -> Result<(), ()> {
    let mut status = Ok(());
    let tests = compile_tests().unwrap_or_else(|e| {
        println!("{}", e);
        status = Err(());
        vec![]
    });

    for test in tests {
        let test_name = &test.name;
        let test_runner: Box<dyn TestRunner> = create_test_runner(config, &test);
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
