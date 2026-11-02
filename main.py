import sys
import os
import ctypes
import traceback
from datetime import datetime
from PyQt6.QtCore import Qt, QTimer, QRect
from PyQt6.QtWidgets import QApplication
import win32event
import win32api
import winerror

from income_model import IncomeMath, IncomeSnapshot, IncomeSettings
from config_manager import ConfigManager
from ui_capsule import TaskbarCapsule
from ui_popover import IncomeDetailsPopover
from ui_settings import SettingsDialog
from tray_manager import TrayManager
from theme import refresh_screen_cache

# Set DPI awareness for crisp text on Windows 10/11
try:
    ctypes.windll.shcore.SetProcessDpiAwareness(2)
except Exception:
    try:
        ctypes.windll.user32.SetProcessDPIAware()
    except Exception:
        pass

def global_exception_handler(exctype, value, tb):
    """Prevent PyQt6 from aborting on unhandled Python exceptions in slots."""
    err_msg = "".join(traceback.format_exception(exctype, value, tb))
    try:
        appdata = os.environ.get("APPDATA", os.path.expanduser("~"))
        log_path = os.path.join(appdata, "DeskIncome", "error.log")
        os.makedirs(os.path.dirname(log_path), exist_ok=True)
        with open(log_path, "a", encoding="utf-8") as f:
            f.write(f"\n[{datetime.now().isoformat()}]\n{err_msg}\n")
    except Exception:
        pass
    print(err_msg, file=sys.stderr)

sys.excepthook = global_exception_handler

class DeskIncomeApp:
    def __init__(self):
        self.config_manager = ConfigManager()
        self.settings = self.config_manager.settings

        # UI Components
        self.capsule = TaskbarCapsule(self.config_manager)
        self.tray = TrayManager(self.config_manager)
        self.popover = IncomeDetailsPopover(self.config_manager)
        self.settings_dialog = None

        # Wire Signals
        self.capsule.clicked.connect(self.toggle_popover_from_capsule)
        self.capsule.request_settings.connect(self.show_settings)
        self.capsule.request_quit.connect(self.quit_app)
        self.capsule.autostart_toggled.connect(self.set_autostart)

        self.tray.clicked.connect(self.toggle_popover_from_tray)
        self.tray.request_settings.connect(self.show_settings)
        self.tray.request_dock.connect(self.capsule.action_dock_triggered)
        self.tray.toggle_capsule_visibility.connect(self.toggle_capsule_visibility)
        self.tray.toggle_lock.connect(self.capsule.toggle_lock)
        self.tray.autostart_toggled.connect(self.set_autostart)
        self.tray.request_quit.connect(self.quit_app)

        self.popover.request_settings.connect(self.show_settings)
        self.popover.request_quit.connect(self.quit_app)
        self.popover.autostart_toggled.connect(self.set_autostart)
        self.popover.request_dock.connect(self.capsule.action_dock_triggered)

        # 1-second update timer
        self.timer = QTimer()
        self.timer.timeout.connect(self.tick)
        self.timer.start(1000)

        # Capture clean initial screen before showing any glass window
        refresh_screen_cache()

        # Apply initial visibility
        if self.config_manager.show_capsule:
            self.capsule.adapt_position(force=True)
            self.capsule.show()
            self.capsule.adapt_position(force=True)
        else:
            self.capsule.hide()

        self.tick()

        # First launch experience
        if not self.config_manager.has_launched:
            self.config_manager.has_launched = True
            self.config_manager.save()
            QTimer.singleShot(400, self.show_settings)

    def tick(self):
        now = datetime.now()
        snapshot = IncomeMath.snapshot(self.settings, now)
        self.capsule.update_snapshot(snapshot, self.settings)
        self.tray.update_snapshot(snapshot, self.settings)

        if self.popover.isVisible():
            self.popover.update_data(snapshot, self.settings, now)

    def toggle_popover_from_capsule(self):
        import time
        if self.popover.isVisible():
            self.popover.hide()
        else:
            if time.monotonic() - getattr(self.popover, "last_hide_time", 0.0) < 0.28:
                return
            now = datetime.now()
            snapshot = IncomeMath.snapshot(self.settings, now)
            self.popover.update_styles()
            self.popover.update_data(snapshot, self.settings, now)
            self.popover.position_near(self.capsule.geometry())
            self.popover.show()
            self.popover.activateWindow()

    def toggle_popover_from_tray(self):
        import time
        if self.popover.isVisible():
            self.popover.hide()
        else:
            if time.monotonic() - getattr(self.popover, "last_hide_time", 0.0) < 0.28:
                return
            now = datetime.now()
            snapshot = IncomeMath.snapshot(self.settings, now)
            self.popover.update_styles()
            self.popover.update_data(snapshot, self.settings, now)

            tray_geo = self.tray.geometry()
            if tray_geo.isValid() and tray_geo.width() > 0:
                self.popover.position_near(tray_geo)
            elif self.capsule.isVisible():
                self.popover.position_near(self.capsule.geometry())
            else:
                screen = QApplication.primaryScreen().geometry()
                dummy_rect = QRect(screen.right() - 100, screen.bottom() - 40, 40, 40)
                self.popover.position_near(dummy_rect)

            self.popover.show()
            self.popover.activateWindow()

    def toggle_capsule_visibility(self):
        self.config_manager.show_capsule = not self.config_manager.show_capsule
        self.config_manager.save()
        if self.config_manager.show_capsule:
            self.capsule.show()
            self.capsule.adapt_position(force=True)
        else:
            self.capsule.hide()
        self.tray.act_show_capsule.setChecked(self.config_manager.show_capsule)

    def show_settings(self):
        self.popover.hide()
        QApplication.processEvents()
        cur_mode = getattr(self.config_manager, "dock_mode", "tray_left")
        if self.settings_dialog is None or not self.settings_dialog.isVisible():
            self.settings_dialog = SettingsDialog(self.settings, current_dock_mode=cur_mode)
            self.settings_dialog.saved.connect(self.on_settings_saved)
            self.settings_dialog.dock_mode_changed.connect(self.capsule.set_dock_mode)
            self.settings_dialog.show()
            self.settings_dialog.raise_()
            self.settings_dialog.activateWindow()
        else:
            self.settings_dialog.load_settings(self.settings, cur_mode)
            self.settings_dialog.update_styles()
            self.settings_dialog.refresh_backdrop()
            self.settings_dialog.show()
            self.settings_dialog.raise_()
            self.settings_dialog.activateWindow()

    def on_settings_saved(self, new_settings: IncomeSettings):
        self.settings = new_settings
        self.config_manager.settings = new_settings
        self.config_manager.save()
        self.tick()

    def set_autostart(self, enable: bool):
        self.config_manager.set_autostart(enable)
        self.tray.act_autostart.setChecked(enable)

    def quit_app(self):
        self.capsule.close()
        self.popover.close()
        if self.settings_dialog:
            self.settings_dialog.close()
        QApplication.quit()

def main():
    mutex = win32event.CreateMutex(None, False, "DeskIncome_SingleInstance_Mutex_2026")
    if win32api.GetLastError() == winerror.ERROR_ALREADY_EXISTS:
        print("DeskIncome is already running.")
        sys.exit(0)

    app = QApplication(sys.argv)
    app.setQuitOnLastWindowClosed(False)

    app_instance = DeskIncomeApp()
    sys.exit(app.exec())

if __name__ == "__main__":
    main()
