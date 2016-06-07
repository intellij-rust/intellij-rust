/// Docs
#[cfg(test)]
/// More Docs
pub const unsafe extern "C" fn foo<T>(x: T) -> u32 where T: Clone { 92 }

fn main() {
    <caret>foo()
}
