fn main() {
    if let Err(err) = try_main() {
        eprintln!("{}", err);
        std::process::exit(101);
    }
}

fn try_main() -> std::io::Result<()> {
    ra_ap_proc_macro_srv::cli::run()
}
