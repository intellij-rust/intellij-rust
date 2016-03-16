mod foo {
    use bar::E::{<caret>X};
}

mod bar {
    pub enum E {
        X
    }
}
