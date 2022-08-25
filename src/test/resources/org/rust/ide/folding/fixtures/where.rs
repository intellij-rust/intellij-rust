fn bar() where <fold text='{...}'>{}</fold>

fn foo<X, Y><fold text='(...)'>(x: X, y: Y)</fold> -> ()
where <fold text='...'>
    X: Clone,
    Y: Clone,</fold>
<fold text='{...}'>{

}</fold>

fn foobar<X, Y><fold text='(...)'>(x: X, y: Y)</fold> -> ()
where<fold text='...'>
    X: Clone,
    Y: Clone,</fold>
<fold text='{...}'>{

}</fold>

fn foobar<X, Y><fold text='(...)'>(x: X, y: Y)</fold> -> ()
where
