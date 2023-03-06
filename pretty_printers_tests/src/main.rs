#[macro_use]
extern crate serde_derive;
extern crate test_runner;
extern crate toml;

use std::fs;
use std::fs::read_dir;
use std::path::Path;

use test_runner::{Config, TestResult};
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
    test_dir: String,
    pretty_printers_path: String,
    print_stdout: bool,
    lldb: Option<LLDBSettings>,
}

#[derive(Deserialize)]
struct LLDBSettings {
    python: String,
    lldb_batchmode: String,
    native_rust: bool
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
                test_dir: settings.test_dir.clone(),
                pretty_printers_path: settings.pretty_printers_path,
                print_stdout: settings.print_stdout,
                lldb_python: path,
                lldb_batchmode: lldb_settings.lldb_batchmode,
                python: lldb_settings.python,
                native_rust: lldb_settings.native_rust,
            })
        },

        Debugger::GDB => {
            Config::GDB(GDBConfig {
                test_dir: settings.test_dir.clone(),
                pretty_printers_path: settings.pretty_printers_path,
                print_stdout: settings.print_stdout,
                gdb: path,
            })
        }
    };

    let src_dir = Path::new(&settings.test_dir);
    let src_paths: Vec<_> = read_dir(src_dir)
        .unwrap_or_else(|_| panic!("Tests not found!"))
        .map(|file| file.unwrap().path())
        .map(|path| fs::canonicalize(path))
        .map(|canonical_path| canonical_path.unwrap().as_os_str().to_owned())
        .collect();

    let mut status = Ok(());

    for path in src_paths {
        let path = Path::new(&path);
        let test_runner: Box<dyn TestRunner> = create_test_runner(&config, path);
        let result = test_runner.run();
        let path_string = path.file_name().unwrap().to_str().unwrap();

        match result {
            TestResult::Ok => {
                println!("{}: passed", path_string);
            },
            TestResult::Skipped(reason) => {
                println!("{}: skipped", path_string);
                println!("{}", reason);
            },
            TestResult::Err(reason) => {
                println!("{}: failed", path_string);
                println!("{}", reason);
                status = Err(());
            },
        }
    }

    status
}
