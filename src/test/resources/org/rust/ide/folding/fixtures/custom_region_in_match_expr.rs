fn test() <fold text='{...}'>{
    match 1 <fold text='{...}'>{
        <fold text='1'>//region 1
        1 => println!("Hello!"),
        //endregion</fold>
        <fold text='2,3,_'>//region 2,3,_
        2 | 3 => println!("World!"),
        _ => panic!(),
        //endregion</fold>
    }</fold>;
}</fold>
