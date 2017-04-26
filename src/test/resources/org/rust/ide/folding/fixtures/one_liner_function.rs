fn foo()<fold text=' { '> {
    </fold>println!("Hello World");<fold text=' }'>
}</fold>

fn bar()
<fold text='{...}'>{

    println!("Hello World");

}</fold>

fn long() <fold text='{...}'>{
    println!("This line is too long to fit into the line margin after collapsing, so it will fully collapse as a large block");
}</fold>
