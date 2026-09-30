use std::ffi::{CStr, CString};
use std::process;
use std::ptr;
use winapi::shared::minwindef::FALSE;
use winapi::um::handleapi::{CloseHandle, INVALID_HANDLE_VALUE}; // Import here
use winapi::um::libloaderapi::{GetModuleHandleA, GetProcAddress};
use winapi::um::memoryapi::{VirtualAllocEx, WriteProcessMemory};
use winapi::um::processthreadsapi::{CreateRemoteThread, OpenProcess};
use winapi::um::tlhelp32::{CreateToolhelp32Snapshot, Process32First, Process32Next, PROCESSENTRY32};
use winapi::um::winnt::{MEM_COMMIT, MEM_RESERVE, PAGE_READWRITE, PROCESS_ALL_ACCESS};

fn find_process_by_name(process_name: &str) -> Option<u32> {
    unsafe {
        let snapshot = CreateToolhelp32Snapshot(winapi::um::tlhelp32::TH32CS_SNAPPROCESS, 0);
        if snapshot == INVALID_HANDLE_VALUE {
            eprintln!("Failed to create process snapshot.");
            return None;
        }

        let mut entry: PROCESSENTRY32 = std::mem::zeroed();
        entry.dwSize = std::mem::size_of::<PROCESSENTRY32>() as u32;

        if Process32First(snapshot, &mut entry) == FALSE {
            CloseHandle(snapshot);
            eprintln!("Failed to retrieve the first process.");
            return None;
        }

        loop {
            let exe_name = CStr::from_ptr(entry.szExeFile.as_ptr());
            if exe_name.to_string_lossy() == process_name {
                CloseHandle(snapshot);
                return Some(entry.th32ProcessID);
            }

            if Process32Next(snapshot, &mut entry) == FALSE {
                break;
            }
        }

        CloseHandle(snapshot);
    }
    None
}
// cd C:\Users\TCL87\Documents\GitHub\UID\libs
fn main() {
    let process_name = "javaw.exe";
    let dll_path = r"C:\Program Files (x86)\Steam\vstvfib.dll";
    let target_id = find_process_by_name(process_name).unwrap_or_else(|| {
        eprintln!("Process '{}' not found.", process_name);
        process::exit(1);
    });

    let handle = unsafe { OpenProcess(PROCESS_ALL_ACCESS, 0, target_id) };
    if handle.is_null() {
        eprintln!(
            "Failed to obtain handle to process with process id {}",
            &target_id
        );
        process::exit(1);
    }

    let alloc_size = dll_path.len() + 1;
    let mem_alloc = unsafe {
        VirtualAllocEx(
            handle,
            ptr::null_mut(),
            alloc_size,
            MEM_COMMIT | MEM_RESERVE,
            PAGE_READWRITE,
        )
    };

    if mem_alloc.is_null() {
        eprintln!("Failed to allocate memory in process.");
        unsafe { CloseHandle(handle) };
        process::exit(1);
    }

    let dll_path_cstr = CString::new(dll_path).expect("Failed to create CString from dll_path");
    let write_mem = unsafe {
        WriteProcessMemory(
            handle,
            mem_alloc,
            dll_path_cstr.as_ptr() as *const _,
            alloc_size,
            ptr::null_mut(),
        )
    };

    if write_mem == 0 {
        eprintln!("Failed to write DLL path into target process.");
        unsafe { CloseHandle(handle) };
        process::exit(1);
    }

    let kernel32 = unsafe { GetModuleHandleA("kernel32.dll\0".as_ptr() as *const i8) };
    let load_library = unsafe {
        GetProcAddress(kernel32, "LoadLibraryA\0".as_ptr() as *const i8)
    };

    if load_library.is_null() {
        eprintln!("Failed to get memory address of LoadLibraryA.");
        unsafe { CloseHandle(handle) };
        process::exit(1);
    }

    let thread_handle = unsafe {
        CreateRemoteThread(
            handle,
            ptr::null_mut(),
            0,
            Some(std::mem::transmute(load_library)),
            mem_alloc,
            0,
            ptr::null_mut(),
        )
    };

    if thread_handle.is_null() {
        eprintln!("Failed to create remote thread.");
        unsafe { CloseHandle(handle) };
        process::exit(1);
    }

    unsafe {
        CloseHandle(thread_handle);
        CloseHandle(handle);
    }

    println!("Successfully injected DLL into {}.", process_name);
}
