#[derive(Clone, Copy, PartialEq)]
pub enum CompileMode {
    Test,
    Build,
    Bench,
    Doc { deps: bool },
}

pub enum CompileFilter<'a> {
    Everything,
    Only {
        lib: bool,
        bins: &'a [String],
        examples: &'a [String],
        tests: &'a [String],
        benches: &'a [String],
    }
}
