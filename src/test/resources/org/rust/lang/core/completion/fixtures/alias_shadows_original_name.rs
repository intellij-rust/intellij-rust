mod m {
    pub fn transmogrify() {}
}

use self::m::{transmogrify as frobnicate};

fn main() {
    trans<caret>
}
