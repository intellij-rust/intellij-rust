use std::{env, io, process};

mod rustc_wrapper;

fn main() {
    if let Err(err) = try_main() {
        eprintln!("{}", err);
        process::exit(101);
    }
}

fn try_main() -> io::Result<()> {
    let mut args = env::args_os().skip(1);
    return if let Some(rustc_executable) = args.next() {
        let args: Vec<_> = args.collect();
        match rustc_wrapper::run_rustc_skipping_cargo_checking(rustc_executable, args)?.0 {
            None => process::exit(102), // None if killed
            Some(exit_code) => process::exit(exit_code)
        }
    } else {
        ra_ap_proc_macro_srv::cli::run()
    };
}
