
// That's a sample of ill-formed (incomplete precisely speaking) snippets
// that should nevertheless get almost canonical PSI structure

// Trailing decl
fn add(x: i32,

// Trailing '{'
fn add(x: i32, y: i32) -> i32 {


// Trailing 'expr'
fn add0(x: i32, y: i32) -> i32 {
  x + y

fn wrong_comma(,) -> i32 {
}