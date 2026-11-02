from __future__ import annotations
import os
import sys
from typing import Optional
from PyQt6.QtCore import Qt, QPoint, QRectF, pyqtSignal, QObject
from PyQt6.QtWidgets import QSystemTrayIcon, QMenu
from PyQt6.QtGui import QIcon, QPixmap, QPainter, QColor, QFont, QPen, QBrush

from income_model import IncomeSettings, IncomeSnapshot
from theme import get_theme_colors

class TrayManager(QObject):
    clicked = pyqtSignal()
    request_settings = pyqtSignal()
    request_dock = pyqtSignal()
    toggle_capsule_visibility = pyqtSignal()
    toggle_lock = pyqtSignal()
    autostart_toggled = pyqtSignal(bool)
    request_quit = pyqtSignal()

    def __init__(self, config_manager, parent=None):
        super().__init__(parent)
        self.config_manager = config_manager
        self.tray_icon = QSystemTrayIcon(parent)
        self.settings: IncomeSettings = config_manager.settings
        self.snapshot: Optional[IncomeSnapshot] = None

        self.init_icon()
        self.init_menu()
        self.tray_icon.activated.connect(self.on_activated)
        self.tray_icon.show()

    def init_icon(self):
        base_dir = getattr(sys, "_MEIPASS", os.path.dirname(os.path.abspath(__file__)))
        icon_path = os.path.join(base_dir, "app_icon.png")
        if os.path.exists(icon_path) and self.settings.currency == "CNY":
            self.tray_icon.setIcon(QIcon(icon_path))
            return

        pixmap = QPixmap(64, 64)
        pixmap.fill(Qt.GlobalColor.transparent)

        painter = QPainter(pixmap)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)

        rect = QRectF(4, 4, 56, 56)
        painter.setPen(QPen(QColor(230, 180, 20), 2.5))
        painter.setBrush(QBrush(QColor(245, 195, 35)))
        painter.drawEllipse(rect)

        inner_rect = QRectF(10, 10, 44, 44)
        painter.setPen(QPen(QColor(215, 160, 10, 180), 1.5))
        painter.setBrush(Qt.BrushStyle.NoBrush)
        painter.drawEllipse(inner_rect)

        font = QFont("Segoe UI Variable Display", 22, QFont.Weight.Bold)
        painter.setFont(font)
        painter.setPen(QColor(80, 50, 0))
        symbol = self.settings.CURRENCY_SYMBOLS.get(self.settings.currency, "¥").strip()
        if len(symbol) > 2:
            symbol = symbol[0]
        painter.drawText(QRectF(0, 0, 64, 64), Qt.AlignmentFlag.AlignCenter, symbol)
        painter.end()

        self.tray_icon.setIcon(QIcon(pixmap))

    def init_menu(self):
        self.menu = QMenu()
        theme = get_theme_colors()

        bg = "#202634" if theme.is_dark else "#F8FAFC"
        fg = "#F8FAFC" if theme.is_dark else "#0F172A"
        hl_bg = "rgba(59, 130, 246, 0.25)" if theme.is_dark else "rgba(0, 103, 192, 0.12)"
        border_c = "rgba(255, 255, 255, 0.22)" if theme.is_dark else "rgba(15, 23, 42, 0.14)"

        self.menu.setStyleSheet(f"""
            QMenu {{
                background-color: {bg};
                color: {fg};
                border: 1px solid {border_c};
                border-radius: 10px;
                padding: 5px;
                font-size: 12px;
            }}
            QMenu::item {{
                padding: 6px 18px;
                border-radius: 6px;
            }}
            QMenu::item:selected {{
                background-color: {hl_bg};
            }}
            QMenu::separator {{
                height: 1px;
                background: {border_c};
                margin: 4px 8px;
            }}
        """)

        act_details = self.menu.addAction("今日收入详情…")
        act_details.triggered.connect(self.clicked.emit)

        act_settings = self.menu.addAction("修改工资与作息…")
        act_settings.triggered.connect(self.request_settings.emit)

        self.menu.addSeparator()

        act_dock = self.menu.addAction("自适应跟随托盘左侧")
        act_dock.triggered.connect(self.request_dock.emit)

        self.act_show_capsule = self.menu.addAction("显示状态栏液态玻璃胶囊")
        self.act_show_capsule.setCheckable(True)
        self.act_show_capsule.setChecked(self.config_manager.show_capsule)
        self.act_show_capsule.triggered.connect(self.toggle_capsule_visibility.emit)

        self.act_lock = self.menu.addAction("锁定状态栏胶囊位置")
        self.act_lock.setCheckable(True)
        self.act_lock.setChecked(self.config_manager.locked)
        self.act_lock.triggered.connect(self.toggle_lock.emit)

        self.act_autostart = self.menu.addAction("开机自动启动")
        self.act_autostart.setCheckable(True)
        self.act_autostart.setChecked(self.config_manager.is_autostart_enabled())
        self.act_autostart.toggled.connect(self.autostart_toggled.emit)

        self.menu.addSeparator()

        act_quit = self.menu.addAction("退出今日收入")
        act_quit.triggered.connect(self.request_quit.emit)

        self.tray_icon.setContextMenu(self.menu)

    def update_snapshot(self, snapshot: IncomeSnapshot, settings: IncomeSettings):
        self.snapshot = snapshot
        if settings.currency != self.settings.currency:
            self.settings = settings
            self.init_icon()
        self.settings = settings

        amount_str = settings.money(snapshot.earned)
        pct = int(round(snapshot.progress * 100))
        tooltip = f"今日收入: {amount_str} · {snapshot.status_label}\n工作进度: {pct}%\n点击展开详情，右键打开菜单"
        self.tray_icon.setToolTip(tooltip)

        self.act_autostart.setChecked(self.config_manager.is_autostart_enabled())
        self.act_lock.setChecked(self.config_manager.locked)
        self.act_show_capsule.setChecked(self.config_manager.show_capsule)

    def on_activated(self, reason: QSystemTrayIcon.ActivationReason):
        if reason in (QSystemTrayIcon.ActivationReason.Trigger, QSystemTrayIcon.ActivationReason.DoubleClick):
            self.clicked.emit()

    def geometry(self):
        return self.tray_icon.geometry()
