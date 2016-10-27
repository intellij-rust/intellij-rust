fn foo<'a, 'b>(t: &'a i32, f: &'b i32) where 'b: 'a<caret> {

}
