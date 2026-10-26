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
        let mut output = io::stdout().lock();
        if response.trailing_newline {
            writeln!(output, "{}", response.text)
        } else {
            output
                .write_all(response.text.as_bytes())
                .and_then(|()| output.flush())
        }
    };
    if let Err(error) = result {
        let _ = writeln!(io::stderr().lock(), "qms: output failed: {error}");
        return ExitCode::from(1);
    }
    ExitCode::from(response.code)
}
