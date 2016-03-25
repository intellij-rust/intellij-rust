mod foo {
    pub fn quux() {}
}

use foo::{q<caret>};

fn main() {}
