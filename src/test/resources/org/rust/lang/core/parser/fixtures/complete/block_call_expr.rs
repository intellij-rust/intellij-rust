fn f() {
    { foo } ();
    ({ foo }) ();
    let _ = { bar }();
}
