use std::{env, fs};
use std::path::Path;
use quote::ToTokens;
use reqwest::blocking::get;
use syn::{File, Item};

fn main() {
    let commit = env::var("RUSTC_COMMIT").unwrap_or(String::from("master"));
    let text = get(format!("https://raw.githubusercontent.com/rust-lang/rust/{commit}/compiler/rustc_span/src/symbol.rs"))
        .unwrap()
        .text()
        .unwrap();

    let result = syn::parse_str::<File>(&text).unwrap();
    for item in result.items {
        if let Item::Macro(m) = item {
            let name = m.mac.path.segments.to_token_stream().to_string();
            if name == "symbols" {
                let text = m.mac.to_token_stream().to_string();
                let out_dir = env::var("OUT_DIR").unwrap();
                let dest_path = Path::new(&out_dir).join("symbol.rs");
                fs::write(dest_path, &text).unwrap();
                return;
            }
        }
    }
    panic!("Can't find `symbols` macro call");
}
