pub mod bar;

mod foo {
    pub use super::bar::b<caret>az;
}
