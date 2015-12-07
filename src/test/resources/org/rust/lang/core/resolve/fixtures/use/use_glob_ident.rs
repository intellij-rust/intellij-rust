mod foo {
    use bar::{h<caret>ello as h};
}

pub mod bar {
    pub fn hello() { }
}
