#[macro_use]
extern crate serde_derive;
extern crate test_runner;
extern crate toml;

use std::fs;
use std::fs::read_dir;
use std::path::Path;

use test_runner::{LLDBConfig, LLDBTestRunner};

#[cfg(target_os = "linux")]
const SETTINGS: &str = "Settings_linux.toml";
#[cfg(target_os = "macos")]
const SETTINGS: &str = "Settings_macos.toml";
#[cfg(all(not(target_os = "linux"), not(target_os = "macos")))]
panic!("Unsupported platform");

#[derive(Deserialize)]
struct Settings {
    test_dir: String,
    pretty_printers_path: String,
    lldb_batchmode: String,
    lldb_lookup: String,
    python: String,
    print_lldb_stdout: bool,
    lldb_native_rust: bool,
}

/// Expects `lldb_python` path as the first argument
fn main() -> Result<(), ()> {
    let args: Vec<String> = std::env::args().collect();
    let lldb_python = args.get(1).expect("You need to pass a path to `lldb_python`").clone();
    let settings = fs::read_to_string(SETTINGS).expect(&format!("Cannot read {}", SETTINGS));
    let settings: Settings = toml::from_str(&settings).expect(&format!("Invalid {}", SETTINGS));
    let lldb_config = LLDBConfig {
        test_dir: settings.test_dir,
        pretty_printers_path: settings.pretty_printers_path,
        lldb_batchmode: settings.lldb_batchmode,
        lldb_lookup: settings.lldb_lookup,
        lldb_python,
        python: settings.python,
        print_lldb_stdout: settings.print_lldb_stdout,
        lldb_native_rust: settings.lldb_native_rust
    };

    let src_dir = Path::new(&lldb_config.test_dir);
    let src_paths: Vec<_> = read_dir(src_dir)
        .unwrap_or_else(|_| panic!("Tests not found!"))
        .map(|file| file.unwrap().path().as_os_str().to_owned())
        .collect();

    let mut status = Ok(());

    for path in src_paths {
        let path = Path::new(&path);
        let test_runner = LLDBTestRunner { config: &lldb_config, src_path: path };
        let result = test_runner.run();
        let path_string = path.file_name().unwrap().to_str().unwrap();

        match result {
            Ok(_) => {
                println!("{}: passed", path_string);
            }
            Err(e) => {
                println!("{}: failed", path_string);
                println!("{}", e);
                status = Err(());
            }
        }
    }

    status
}
