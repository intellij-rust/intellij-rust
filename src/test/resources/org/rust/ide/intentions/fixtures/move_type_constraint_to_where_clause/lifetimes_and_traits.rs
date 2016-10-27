fn foo<'a, 'b: 'a, T: Send,<caret> F: Sync>(t: &'a T, f: &'b F) {

}
