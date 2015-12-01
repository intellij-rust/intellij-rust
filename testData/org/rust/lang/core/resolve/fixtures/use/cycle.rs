// This is a loop: it simultaneously defines and imports `foo`.
use foo;
use bar::baz;

fn main() {
    <caret>foo();
}
