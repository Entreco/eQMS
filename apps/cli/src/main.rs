mod cli;
mod commands;
mod output;

use std::io::{self, Write};
use std::process::ExitCode;

fn main() -> ExitCode {
    let response = cli::response(std::env::args_os().collect(), &mut io::stdin().lock());
    let result = if response.error {
        writeln!(io::stderr().lock(), "{}", response.text)
    } else {
        writeln!(io::stdout().lock(), "{}", response.text)
    };
    if let Err(error) = result {
        let _ = writeln!(io::stderr().lock(), "qms: output failed: {error}");
        return ExitCode::from(1);
    }
    ExitCode::from(response.code)
}
