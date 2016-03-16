fn foo() {}

#[cfg(test)]
mod tests {
    use super::*;

    fn test_foo() {
        <caret>foo();
    }
}
