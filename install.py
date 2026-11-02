import os
import sys
import winreg
import subprocess
from pathlib import Path

def setup_installation():
    current_dir = os.path.dirname(os.path.abspath(__file__))
    exe_path = os.path.join(current_dir, "今日收入.exe")

    if not os.path.exists(exe_path):
        print(f"Error: {exe_path} not found!")
        return False

    # 1. Register Autostart in Windows Registry
    print("1. Configuring Windows Autostart...")
    run_key = r"Software\Microsoft\Windows\CurrentVersion\Run"
    try:
        key = winreg.OpenKey(
            winreg.HKEY_CURRENT_USER,
            run_key,
            0,
            winreg.KEY_SET_VALUE
        )
        winreg.SetValueEx(key, "DeskIncome", 0, winreg.REG_SZ, f'"{exe_path}"')
        winreg.CloseKey(key)
        print("   -> Successfully registered to Windows Startup (HKCU Run).")
    except Exception as e:
        print(f"   -> Failed to register autostart: {e}")

    # 2. Create Desktop Shortcut
    print("2. Creating Desktop Shortcut...")
    try:
        import win32com.client
        shell = win32com.client.Dispatch("WScript.Shell")
        desktop_dir = shell.SpecialFolders("Desktop")
        shortcut_path = os.path.join(desktop_dir, "今日收入.lnk")
        shortcut = shell.CreateShortCut(shortcut_path)
        shortcut.TargetPath = exe_path
        shortcut.WorkingDirectory = current_dir
        shortcut.IconLocation = exe_path
        shortcut.Description = "今日收入 · 任务栏与状态栏工资计数器"
        shortcut.Save()
        print(f"   -> Shortcut created at: {shortcut_path}")
    except Exception as e:
        print(f"   -> Could not create shortcut via COM: {e}")

    # 3. Launch the Application Now
    print("3. Launching '今日收入.exe' now...")
    try:
        # Check if already running
        tasklist = subprocess.run(["tasklist", "/FI", "IMAGENAME eq 今日收入.exe"], capture_output=True, text=True)
        if "今日收入.exe" in tasklist.stdout:
            print("   -> '今日收入.exe' is already running!")
        else:
            # Start process detached
            cmd = f'start "" "{exe_path}"'
            subprocess.Popen(cmd, shell=True, cwd=current_dir)
            print("   -> Successfully launched '今日收入.exe'!")
    except Exception as e:
        print(f"   -> Failed to launch: {e}")

    return True

if __name__ == "__main__":
    setup_installation()
