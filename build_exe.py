import subprocess
import sys
import os

def build():
    cmd = [
        sys.executable,
        "-m", "PyInstaller",
        "--noconsole",
        "--onefile",
        "--icon=app_icon.ico",
        "--add-data=app_icon.png;.",
        "--exclude-module=pandas",
        "--exclude-module=openpyxl",
        "--name=今日收入",
        "main.py"
    ]
    print("Running:", " ".join(cmd))
    res = subprocess.run(cmd, cwd=os.path.dirname(os.path.abspath(__file__)))
    if res.returncode == 0:
        print("\nBUILD SUCCESSFUL! Output: dist/今日收入.exe")
    else:
        print("\nBUILD FAILED with code", res.returncode)

if __name__ == "__main__":
    build()
