fn main() {
    let x = 5;

    if <warning descr="Unnecessary parenthesis">(x == 5)</warning> {
        println!("x is five!");
    } else {
        println!("x is not five :(");
    }
}
