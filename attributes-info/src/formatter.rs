use std::io;
use std::io::Write;

use serde_json::ser::{Formatter, PrettyFormatter};

pub(crate) struct MyFormatter {
    pretty_formatter: PrettyFormatter<'static>
}

impl MyFormatter {
    pub(crate) fn new() -> Self {
        MyFormatter { pretty_formatter: PrettyFormatter::new() }
    }
}

impl Formatter for MyFormatter {
    fn begin_array<W: ?Sized + Write>(&mut self, writer: &mut W) -> io::Result<()> {
        self.pretty_formatter.begin_array(writer)
    }

    fn end_array<W: ?Sized + Write>(&mut self, writer: &mut W) -> io::Result<()> {
        self.pretty_formatter.end_array(writer)
    }

    fn begin_array_value<W: ?Sized + Write>(&mut self, writer: &mut W, first: bool) -> io::Result<()> {
        self.pretty_formatter.begin_array_value(writer, first)
    }

    fn end_array_value<W: ?Sized + Write>(&mut self, writer: &mut W) -> io::Result<()> {
        self.pretty_formatter.end_array_value(writer)
    }
}
