fn foo<'a, 'b, T, F>(t: &'a T, f: &'b F) where 'b: 'a, T: Send, F: Sync<caret> {

}
