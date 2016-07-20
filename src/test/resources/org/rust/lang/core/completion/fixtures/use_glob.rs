mod foo {
    pub fn quux() {}
}

use self::foo::{q<caret>};

fn main() {}
