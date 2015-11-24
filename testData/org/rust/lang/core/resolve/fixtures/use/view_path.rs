mod foo {
    use ::bar::h<caret>ello;
}

pub mod bar {
    pub fn hello() { }
}
