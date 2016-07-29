fn main() {
    let a = {
        let inner = 3;
        (inner * inner)<caret>
    };
}
