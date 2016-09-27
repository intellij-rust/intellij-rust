use super::S;

pub mod nested;


fn foo() {
    #[path="spam.rs"]
    mod spam;
}
