extern crate test_package;
fn test3a() {
    test_package::inner1::inner3::mod2::foo::func();
}
fn test3b() {
    use test_package::inner1::inner3::mod2::foo::func;
}
