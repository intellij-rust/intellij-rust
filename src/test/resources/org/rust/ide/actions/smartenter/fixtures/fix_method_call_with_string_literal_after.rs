fn f(s: String) -> String {
    f(f(f("((")));
    <caret>
}
