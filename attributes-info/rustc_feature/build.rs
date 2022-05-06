use std::{env, fs};
use std::path::Path;

use reqwest::blocking::get;

fn main() {
    download_file("https://raw.githubusercontent.com/rust-lang/rust/master/compiler/rustc_feature/src/active.rs", "active.rs");
    download_file("https://raw.githubusercontent.com/rust-lang/rust/master/compiler/rustc_feature/src/accepted.rs", "accepted.rs");
    download_file("https://raw.githubusercontent.com/rust-lang/rust/master/compiler/rustc_feature/src/removed.rs", "removed.rs");
    download_file("https://raw.githubusercontent.com/rust-lang/rust/master/compiler/rustc_feature/src/builtin_attrs.rs", "builtin_attrs.rs");
}

fn download_file(url: &str, file_name: &str) {
    let text = get(url)
        .unwrap()
        .text()
        .unwrap()
        .lines()
        .skip_while(|line| line.starts_with("//!"))
        .collect::<Vec<_>>()
        .join("\n");

    let out_dir = env::var("OUT_DIR").unwrap();
    let dest_path = Path::new(&out_dir).join(file_name);
    fs::write(dest_path, &text).unwrap();
}
