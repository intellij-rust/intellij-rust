mod foo {
    pub fn quux() {}
}

use self::foo::{quux};

fn main() {}
