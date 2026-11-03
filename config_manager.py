import os
import sys
import json
import winreg
from pathlib import Path
from typing import Optional, Tuple
from income_model import IncomeSettings

class ConfigManager:
    def __init__(self):
        appdata = os.environ.get("APPDATA")
        if appdata:
            self.config_dir = Path(appdata) / "DeskIncome"
        else:
            self.config_dir = Path(os.path.expanduser("~")) / ".deskincome"
        self.config_dir.mkdir(parents=True, exist_ok=True)
        self.config_file = self.config_dir / "config.json"

        self.settings = IncomeSettings()
        self.locked = False
        self.docked = True
        self.dock_mode: str = "tray_left"  # "tray_left", "taskbar_center", "apps_right", "custom"
        self.custom_ratio: Optional[float] = None  # relative ratio along taskbar (0.0 to 1.0)
        self.capsule_pos: Optional[Tuple[int, int]] = None
        self.has_launched = False
        self.show_capsule = True
        self.million_mode = False
        self.load()

    @property
    def effective_settings(self) -> IncomeSettings:
        if self.million_mode:
            import copy
            s = copy.deepcopy(self.settings)
            s.monthly_salary = 1_000_000.0 / 12.0
            return s
        return self.settings

    def load(self):
        if not self.config_file.exists():
            return
        try:
            with open(self.config_file, "r", encoding="utf-8") as f:
                data = json.load(f)
            if "settings" in data:
                self.settings = IncomeSettings.from_dict(data["settings"]).validated()
            self.locked = data.get("locked", False)
            self.docked = data.get("docked", True)
            self.dock_mode = data.get("dock_mode", "tray_left" if self.docked else "custom")
            self.custom_ratio = data.get("custom_ratio", None)
            if "capsule_pos" in data and isinstance(data["capsule_pos"], list) and len(data["capsule_pos"]) == 2:
                self.capsule_pos = (int(data["capsule_pos"][0]), int(data["capsule_pos"][1]))
            self.has_launched = data.get("has_launched", False)
            self.show_capsule = data.get("show_capsule", True)
        except Exception as e:
            print("Failed to load config, using defaults:", e)

    def save(self):
        data = {
            "settings": self.settings.to_dict(),
            "locked": self.locked,
            "docked": self.docked,
            "dock_mode": self.dock_mode,
            "custom_ratio": self.custom_ratio,
            "capsule_pos": list(self.capsule_pos) if self.capsule_pos else None,
            "has_launched": self.has_launched,
            "show_capsule": self.show_capsule,
        }
        try:
            with open(self.config_file, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
        except Exception as e:
            print("Failed to save config:", e)

    @staticmethod
    def get_run_command() -> str:
        if getattr(sys, "frozen", False):
            return f'"{sys.executable}"'
        else:
            py_dir = os.path.dirname(sys.executable)
            pyw = os.path.join(py_dir, "pythonw.exe")
            if not os.path.exists(pyw):
                pyw = sys.executable
            main_py = os.path.abspath(os.path.join(os.path.dirname(__file__), "main.py"))
            return f'"{pyw}" "{main_py}"'

    def is_autostart_enabled(self) -> bool:
        try:
            key = winreg.OpenKey(
                winreg.HKEY_CURRENT_USER,
                r"Software\Microsoft\Windows\CurrentVersion\Run",
                0,
                winreg.KEY_READ
            )
            val, _ = winreg.QueryValueEx(key, "DeskIncome")
            winreg.CloseKey(key)
            return bool(val)
        except FileNotFoundError:
            return False
        except Exception as e:
            print("Failed to check autostart:", e)
            return False

    def set_autostart(self, enable: bool) -> bool:
        run_key = r"Software\Microsoft\Windows\CurrentVersion\Run"
        try:
            if enable:
                key = winreg.OpenKey(
                    winreg.HKEY_CURRENT_USER,
                    run_key,
                    0,
                    winreg.KEY_SET_VALUE
                )
                cmd = self.get_run_command()
                winreg.SetValueEx(key, "DeskIncome", 0, winreg.REG_SZ, cmd)
                winreg.CloseKey(key)
            else:
                try:
                    key = winreg.OpenKey(
                        winreg.HKEY_CURRENT_USER,
                        run_key,
                        0,
                        winreg.KEY_SET_VALUE
                    )
                    winreg.DeleteValue(key, "DeskIncome")
                    winreg.CloseKey(key)
                except FileNotFoundError:
                    pass
            return True
        except Exception as e:
            print("Failed to set autostart:", e)
            return False
