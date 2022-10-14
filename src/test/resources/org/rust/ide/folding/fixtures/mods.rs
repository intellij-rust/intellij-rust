mod <fold text='...'>stdfoo;
mod stdbar</fold>;

type F = ();

mod
mod

type G = ();

<fold text=''>#[cfg_attr(unix, allow(dead_code))]
#[macro_use]
</fold>mod <fold text='...'>macros;
mod convert;
mod callbacks;
#[macro_use]
// Keys comment
<fold text='/* ... */'>/// Keys doc</fold>
mod keys;

<fold text='/* ... */'>/// Lua doc</fold>
mod lua</fold>;

type T = ();

<fold text='/* ... */'><fold text=''>/// Reg doc</fold>
// Reg comment
#[cfg(unix)]
<fold text='/* ... */'>/// Reg doc</fold>
</fold>mod <fold text='...'>registry;
mod commands;

<fold text='/* ... */'>/// Ipc doc</fold>

// Ipc comment
#[cfg(unix)]


<fold text='/* ... */'>/// Ipc doc</fold>
mod ipc;
mod layout;
mod render;
mod wayland;
mod modes;
mod awesome</fold>;

const _: () =();

mod<fold text='...'>
foobar;
mod
barbarbar</fold>;
