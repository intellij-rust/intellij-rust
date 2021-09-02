// min-version: 1.44.0
// max-version: 1.54.0

// === LLDB TESTS ==================================================================================

// lldb-command:run

// lldb-command:print os_string
// lldbr-check:[...]os_string = "A∆й中" [...]
// lldbg-check:[...]$0 = "A∆й中" [...]
// lldb-command:print empty_os_string
// lldbr-check:[...]empty_os_string = "" [...]
// lldbg-check:[...]$1 = "" [...]
// lldb-command:print c_string1
// lldbr-check:[...]c_string1 = "A∆й中" [...]
// lldbg-check:[...]$2 = "A∆й中" [...]
// lldb-command:print path_buf
// lldbr-check:[...]path_buf = "/a/b/∂" [...]
// lldbg-check:[...]$3 = "/a/b/∂" [...]
// lldb-command:print empty_path_buf
// lldbr-check:[...]empty_path_buf = "" [...]
// lldbg-check:[...]$4 = "" [...]
// lldb-command:print os_str1
// lldbr-check:[...]os_str1 = [...] "A∆й中"
// lldbg-check:[...]$5 = [...] "A∆й中"
// lldb-command:print os_str2
// lldbr-check:[...]os_str2 = [...] "A∆й中"
// lldbg-check:[...]$6 = [...] "A∆й中"
// lldb-command:print empty_os_str
// lldbr-check:[...]empty_os_str = [...] ""
// lldbg-check:[...]$7 = [...] ""
// lldb-command:print path1
// lldbr-check:[...]path1 = [...] "/a/b/∂"
// lldbg-check:[...]$8 = [...] "/a/b/∂"
// lldb-command:print empty_path
// lldbr-check:[...]empty_path = [...] ""
// lldbg-check:[...]$9 = [...] ""
// lldb-command:print c_str1
// lldbr-check:[...]c_str1 = [...] "abcd"
// lldbg-check:[...]$10 = [...] "abcd"

// === GDB TESTS ==================================================================================

// gdb-command:run

// gdb-command:print os_string
// gdb-check:[...]$1 = "A∆й中"
// gdb-command:print empty_os_string
// gdb-check:[...]$2 = ""


use std::ffi::{CStr, CString, OsStr, OsString};
use std::ops::DerefMut;
use std::path::{Path, PathBuf};

fn main() {
    let mut os_string = OsString::from("A∆й中");
    let empty_os_string = OsString::from("");
    let c_string1 = CString::new("A∆й中").unwrap();
    let path_buf = PathBuf::from("/a/b/∂");
    let empty_path_buf = PathBuf::from("");
    let os_str1 = OsStr::new("A∆й中");
    let os_str2 = os_string.deref_mut();
    let empty_os_str = OsStr::new("");
    let path1 = Path::new("/a/b/∂");
    let empty_path = Path::new("");
    let c_str1 = CStr::from_bytes_with_nul(b"abcd\0").unwrap();
    print!(""); // #break
}
