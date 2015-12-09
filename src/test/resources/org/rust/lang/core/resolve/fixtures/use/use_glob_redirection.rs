mod foo {
    pub fn a() {}
    pub use bar::{b as c, d as e};
}

mod bar {
    pub use foo::{a as b, c as d};
}

use foo::e;

fn main() {
    <caret>e();
}
