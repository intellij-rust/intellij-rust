// #1
use self :: y :: { self } // missing ';'
fn foo() {} // should be recovered after missing ';'

// #2 speck recover
use {fn, foo};
