mod a {
    pub struct S;
}

mod b {
    pub struct S;
}

mod c {
    pub struct S;
}

use a::*;
use b::S;
use c::*;

fn main() {
    let _ = <caret>S
}
