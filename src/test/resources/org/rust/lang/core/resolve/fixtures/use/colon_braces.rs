struct Spam;

mod foo {
    use ::{Spam};

    fn main() {
        let _: <caret>Spam = unimplemented!();
    }
}
