pub mod bar;

mod foo {
    pub use bar::b<caret>az;
}
