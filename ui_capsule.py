from __future__ import annotations
import time
import math
import ctypes
from typing import Optional
from PyQt6.QtCore import Qt, QPoint, QPointF, QRectF, pyqtSignal, QTimer
from PyQt6.QtWidgets import QWidget, QMenu, QApplication
from PyQt6.QtGui import (
    QPainter, QColor, QFont, QFontMetrics, QPen, QBrush,
    QMouseEvent, QCursor, QPixmap
)
import win32gui
import win32con

from income_model import IncomeSettings, IncomeSnapshot, Status
from theme import (
    get_theme_colors, is_system_dark_mode,
    paint_liquid_glass_pill, capture_frosted_backdrop, refresh_screen_cache
)

GWLP_HWNDPARENT = -8

class TaskbarCapsule(QWidget):
    clicked = pyqtSignal()
    request_settings = pyqtSignal()
    request_quit = pyqtSignal()
    autostart_toggled = pyqtSignal(bool)

    def __init__(self, config_manager, parent=None):
        super().__init__(parent)
        self.config_manager = config_manager
        self.settings: IncomeSettings = config_manager.settings
        self.snapshot: Optional[IncomeSnapshot] = None
        self.amount_text: str = self.settings.money(0.0)
        self.is_hovered: bool = False
        self.hover_pos: Optional[QPointF] = None
        self.drag_start_global: Optional[QPoint] = None
        self.drag_start_win: Optional[QPoint] = None
        self.is_dragging: bool = False
        self.backdrop: Optional[QPixmap] = None
        self._owner_bound_shell: int = 0
        self._last_backdrop_time: float = 0.0

        self.setWindowTitle("今日收入")
        self.setWindowFlags(
            Qt.WindowType.FramelessWindowHint |
            Qt.WindowType.WindowStaysOnTopHint |
            Qt.WindowType.Tool |
            Qt.WindowType.WindowDoesNotAcceptFocus
        )
        self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground, True)
        self.setAttribute(Qt.WidgetAttribute.WA_ShowWithoutActivating, True)
        self.setMouseTracking(True)

        self.capsule_height = 25
        self.capsule_width = 96
        self.resize(self.capsule_width, self.capsule_height)
        self.setCursor(QCursor(Qt.CursorShape.PointingHandCursor))

        # Digit rolling & fast thousandths ticker (macOS parity)
        self.anim_timer = QTimer(self)
        self.anim_timer.timeout.connect(self.update)
        self.transition_from: str = ""
        self.transition_to: str = ""
        self.transition_start_time: float = 0.0
        self.duration: float = 0.8

        # Adaptive timer checks taskbar/tray geometry changes without spamming SetWindowPos
        self.adaptive_timer = QTimer(self)
        self.adaptive_timer.timeout.connect(self.check_docking_and_theme)
        self.adaptive_timer.start(200)

        self.recalc_size()
        self.adapt_position(force=True)

    def _bind_taskbar_owner(self, shell_hwnd: int):
        """
        Binds Shell_TrayWnd as the Win32 Owner (GWLP_HWNDPARENT) of this capsule and sets
        WS_EX_NOACTIVATE. Checks actual OS GW_OWNER (4) and Z-order after the window is visible
        so Qt's show() can never wipe out the owner or leave the capsule behind the taskbar.
        """
        if not shell_hwnd or not self.isVisible():
            return
        try:
            hwnd = int(self.winId())
            actual_owner = ctypes.windll.user32.GetWindow(hwnd, 4)  # GW_OWNER = 4
            if actual_owner != int(shell_hwnd):
                ctypes.windll.user32.SetWindowLongPtrW(hwnd, GWLP_HWNDPARENT, int(shell_hwnd))
                ex_style = win32gui.GetWindowLong(hwnd, win32con.GWL_EXSTYLE)
                win32gui.SetWindowLong(
                    hwnd,
                    win32con.GWL_EXSTYLE,
                    ex_style | win32con.WS_EX_NOACTIVATE | win32con.WS_EX_TOOLWINDOW
                )
                win32gui.SetWindowPos(
                    hwnd,
                    win32con.HWND_TOPMOST,
                    0, 0, 0, 0,
                    win32con.SWP_NOMOVE | win32con.SWP_NOSIZE | win32con.SWP_NOACTIVATE
                )
                self._owner_bound_shell = shell_hwnd
            else:
                # Verify capsule is still above Shell_TrayWnd in Z-order without touching SetWindowPos if already above
                above = False
                cur = win32gui.GetWindow(shell_hwnd, 3)  # GW_HWNDPREV = 3 (walk upwards in Z-order)
                steps = 0
                while cur and steps < 60:
                    if cur == hwnd:
                        above = True
                        break
                    cur = win32gui.GetWindow(cur, 3)
                    steps += 1
                if not above:
                    win32gui.SetWindowPos(
                        hwnd,
                        win32con.HWND_TOPMOST,
                        0, 0, 0, 0,
                        win32con.SWP_NOMOVE | win32con.SWP_NOSIZE | win32con.SWP_NOACTIVATE
                    )
        except Exception:
            pass

    def showEvent(self, event):
        super().showEvent(event)
        shell = win32gui.FindWindow("Shell_TrayWnd", None)
        self._bind_taskbar_owner(shell)
        self.refresh_backdrop()
        QTimer.singleShot(50, lambda: self._bind_taskbar_owner(win32gui.FindWindow("Shell_TrayWnd", None)))
        if self.snapshot and self.snapshot.status == Status.WORKING:
            if not self.anim_timer.isActive():
                self.anim_timer.start(20)

    def hideEvent(self, event):
        if self.anim_timer.isActive():
            self.anim_timer.stop()
        super().hideEvent(event)

    def refresh_backdrop(self):
        self.backdrop = None
        self.update()

    def _get_font(self) -> QFont:
        # Playful, clean rounded sans-serif font
        font = QFont("Segoe UI Variable Display", 9, QFont.Weight.Bold)
        font.setStyleHint(QFont.StyleHint.SansSerif)
        return font

    def check_docking_and_theme(self):
        if not self.is_dragging:
            self.adapt_position(force=False)

    def update_snapshot(self, snapshot: IncomeSnapshot, settings: IncomeSettings):
        self.snapshot = snapshot
        self.settings = settings
        is_working = (snapshot.status == Status.WORKING)
        # 3 decimal places when working (e.g. ¥108.745), 2 decimal places otherwise
        new_text = settings.money(snapshot.earned, digits=3 if is_working else 2)

        now = time.monotonic()
        if not self.transition_to or not is_working:
            self.transition_from = new_text
            self.transition_to = new_text
            self.transition_start_time = now - self.duration
        elif new_text != self.transition_to:
            self.transition_from = self.transition_to
            self.transition_to = new_text
            self.transition_start_time = now

        if new_text != self.amount_text:
            self.amount_text = new_text
            self.recalc_size()

        if is_working and self.isVisible():
            if not self.anim_timer.isActive():
                self.anim_timer.start(20)  # 50 fps smooth ticker
        else:
            if self.anim_timer.isActive():
                self.anim_timer.stop()
            self.update()

        self.setToolTip(
            f"今日收入: {self.amount_text} · {snapshot.status_label}\n"
            f"左键展开详情 · 右键菜单 · 可自由拖动"
        )

    def recalc_size(self):
        font = self._get_font()
        metrics = QFontMetrics(font)
        # Ensure sufficient width for 3 decimals
        ref_text = self.amount_text if len(self.amount_text) >= 8 else (self.amount_text + "0")
        text_width = metrics.horizontalAdvance(ref_text)
        new_width = max(90, text_width + 36)
        if new_width != self.capsule_width:
            self.capsule_width = new_width
            self.resize(self.capsule_width, self.capsule_height)
            if not self.is_dragging:
                self.adapt_position(force=True)
            self.refresh_backdrop()

    def get_taskbar_geometry(self):
        shell = win32gui.FindWindow("Shell_TrayWnd", None)
        if not shell:
            return None
        tb_rect = win32gui.GetWindowRect(shell)
        tray = win32gui.FindWindowEx(shell, 0, "TrayNotifyWnd", None)
        tray_rect = win32gui.GetWindowRect(tray) if tray else None
        rebar = win32gui.FindWindowEx(shell, 0, "ReBarWindow32", None)
        rebar_rect = win32gui.GetWindowRect(rebar) if rebar else None
        return {
            "shell": shell,
            "tb_rect": tb_rect,
            "tray_rect": tray_rect,
            "rebar_rect": rebar_rect
        }

    def adapt_position(self, force: bool = False):
        try:
            info = self.get_taskbar_geometry()
            if not info:
                return

            self._bind_taskbar_owner(info["shell"])

            tb_left, tb_top, tb_right, tb_bottom = info["tb_rect"]
            tb_width = tb_right - tb_left
            tb_height = tb_bottom - tb_top
            if tb_width <= 0 or tb_height <= 0:
                return

            is_horizontal = tb_width >= tb_height
            tray_rect = info["tray_rect"]
            rebar_rect = info["rebar_rect"]

            if is_horizontal:
                ideal_h = min(28, max(22, tb_height - 6))
                if ideal_h != self.capsule_height:
                    self.capsule_height = ideal_h
                    self.resize(self.capsule_width, self.capsule_height)

            mode = getattr(self.config_manager, "dock_mode", "tray_left")
            if not self.config_manager.docked and mode != "custom":
                mode = "custom"

            target_x, target_y = self.x(), self.y()

            if is_horizontal:
                taskbar_center_y = tb_top + (tb_height - self.capsule_height) // 2

                if mode == "tray_left":
                    if tray_rect and tray_rect[0] > tb_left:
                        target_x = tray_rect[0] - self.capsule_width - 8
                    else:
                        target_x = tb_right - self.capsule_width - 180
                    if rebar_rect and rebar_rect[2] > tb_left and target_x < rebar_rect[2] + 6:
                        target_x = rebar_rect[2] + 6
                    target_y = taskbar_center_y

                elif mode == "apps_right":
                    if rebar_rect and rebar_rect[2] > tb_left:
                        target_x = rebar_rect[2] + 10
                    else:
                        target_x = tb_left + 160
                    if tray_rect and target_x + self.capsule_width > tray_rect[0] - 6:
                        target_x = tray_rect[0] - self.capsule_width - 6
                    target_y = taskbar_center_y

                elif mode == "taskbar_center":
                    target_x = tb_left + (tb_width - self.capsule_width) // 2
                    target_y = taskbar_center_y

                elif mode == "custom":
                    if self.config_manager.custom_ratio is not None:
                        target_x = int(tb_left + self.config_manager.custom_ratio * (tb_width - self.capsule_width))
                        target_y = taskbar_center_y
                    elif self.config_manager.capsule_pos is not None:
                        target_x, target_y = self.config_manager.capsule_pos
                    else:
                        target_x = (tray_rect[0] - self.capsule_width - 8) if tray_rect else (tb_right - self.capsule_width - 180)
                        target_y = taskbar_center_y
            else:
                target_x = tb_left + (tb_width - self.capsule_width) // 2
                if mode == "taskbar_center":
                    target_y = tb_top + (tb_height - self.capsule_height) // 2
                elif mode == "apps_right" and rebar_rect:
                    target_y = rebar_rect[3] + 8
                elif tray_rect and tray_rect[1] > tb_top:
                    target_y = tray_rect[1] - self.capsule_height - 8
                else:
                    target_y = tb_bottom - self.capsule_height - 120

            screen_geo = QApplication.primaryScreen().geometry()
            target_x = max(screen_geo.left() + 2, min(screen_geo.right() - self.capsule_width - 2, target_x))
            target_y = max(screen_geo.top() + 1, min(screen_geo.bottom() - self.capsule_height - 1, target_y))

            # ONLY move and refresh backdrop if coordinates actually changed!
            if force or (self.x() != target_x or self.y() != target_y):
                self.move(target_x, target_y)
                self.config_manager.capsule_pos = (target_x, target_y)
                self.refresh_backdrop()
        except Exception:
            pass

    def dock_to_taskbar(self):
        self.config_manager.docked = True
        if self.config_manager.dock_mode == "custom":
            self.config_manager.dock_mode = "tray_left"
        self.adapt_position(force=True)

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing, True)
        painter.setRenderHint(QPainter.RenderHint.TextAntialiasing, True)

        is_dark = is_system_dark_mode()
        pill_rect = QRectF(1.0, 1.0, self.width() - 2.0, self.height() - 2.0)

        paint_liquid_glass_pill(
            painter=painter,
            rect=pill_rect,
            is_dark=is_dark,
            is_hovered=self.is_hovered,
            hover_pos=self.hover_pos,
            backdrop=self.backdrop
        )

        # Status Indicator Jewel Dot (Mint Green / Honey Amber)
        status = self.snapshot.status if self.snapshot else Status.WORKING
        if status == Status.WORKING:
            dot_color = QColor("#10B981")          # Fresh Mint
            glow_color = QColor(16, 185, 129, 115) # Mint Halo
        elif status == Status.LUNCH:
            dot_color = QColor("#F59E0B")          # Honey Amber
            glow_color = QColor(245, 158, 11, 110)
        elif status == Status.BEFORE:
            dot_color = QColor("#D97706")          # Deep Amber
            glow_color = QColor(217, 119, 6, 90)
        elif status == Status.FINISHED:
            dot_color = QColor("#059669") if not is_dark else QColor("#A7F3D0")
            glow_color = QColor(16, 185, 129, 95)
        else:
            dot_color = QColor(107, 114, 128)
            glow_color = QColor(107, 114, 128, 60)

        center_y = self.height() / 2.0
        dot_x = 12.5
        painter.setPen(Qt.PenStyle.NoPen)
        painter.setBrush(QBrush(glow_color))
        painter.drawEllipse(QPointF(dot_x, center_y), 5.0, 5.0)
        painter.setBrush(QBrush(dot_color))
        painter.drawEllipse(QPointF(dot_x, center_y), 3.0, 3.0)
        painter.setBrush(QBrush(QColor(255, 255, 255, 230)))
        painter.drawEllipse(QPointF(dot_x - 0.9, center_y - 0.9), 1.0, 1.0)

        # Money Text with macOS main-branch per-digit rolling & thousandths ticker
        font = self._get_font()
        painter.setFont(font)
        metrics = QFontMetrics(font)

        now = time.monotonic()
        progress = min(1.0, max(0.0, (now - self.transition_start_time) / self.duration))
        eased = progress * progress * (3.0 - 2.0 * progress)  # Smooth cubic Hermite ease
        fast_phase = (now * 12.0) if (status == Status.WORKING) else None

        old_str = self.transition_from or self.amount_text
        new_str = self.transition_to or self.amount_text
        count = max(len(old_str), len(new_str))
        prev_chars = list(" " * (count - len(old_str)) + old_str)
        next_chars = list(" " * (count - len(new_str)) + new_str)

        digit_w = metrics.horizontalAdvance("0")
        widths = []
        for i in range(count):
            c1, c2 = prev_chars[i], next_chars[i]
            if c1.isdigit() or c2.isdigit():
                widths.append(digit_w)
            else:
                widths.append(max(metrics.horizontalAdvance(c1), metrics.horizontalAdvance(c2)))

        total_text_w = sum(widths)

        # Text clipping rectangle (leaves space on left for jewel dot, prevents vertical overflow)
        clip_left = 22.0
        clip_right = self.width() - 8.0
        clip_rect = QRectF(clip_left, 1.0, max(1.0, clip_right - clip_left), self.height() - 2.0)

        painter.save()
        painter.setClipRect(clip_rect)

        avail_w = clip_rect.width()
        start_x = clip_rect.left() + max(0.0, (avail_w - total_text_w) / 2.0)

        ascent = metrics.ascent()
        descent = metrics.descent()
        base_y = center_y + (ascent - descent) / 2.0
        slide_h = 15.0  # Vertical slide travel distance

        cur_x = start_x
        base_color = QColor("#ECFDF5") if is_dark else QColor("#064E3B")
        ticker_color = QColor("#FBBF24") if is_dark else QColor("#F59E0B")

        for i in range(count):
            a = prev_chars[i]
            b = next_chars[i]
            col_w = widths[i]

            # Fast rolling thousandths ticker on the last digit (Honey Amber playful glow)
            if i == count - 1 and fast_phase is not None:
                base_val = int(math.floor(fast_phase))
                fraction = fast_phase - base_val
                g1 = str(base_val % 10)
                g2 = str((base_val + 1) % 10)
                w1 = metrics.horizontalAdvance(g1)
                w2 = metrics.horizontalAdvance(g2)

                # Glyph rolling up smoothly
                painter.setPen(ticker_color)
                painter.drawText(QPointF(cur_x + (col_w - w1) / 2.0, base_y - slide_h * fraction), g1)
                # Next glyph rolling up from below
                painter.drawText(QPointF(cur_x + (col_w - w2) / 2.0, base_y + slide_h * (1.0 - fraction)), g2)

            elif a == b:
                if b != " ":
                    painter.setPen(base_color)
                    w_b = metrics.horizontalAdvance(b)
                    painter.drawText(QPointF(cur_x + (col_w - w_b) / 2.0, base_y), b)
            else:
                # Eased per-digit vertical scroll
                if progress < 1.0 and a != " ":
                    c_old = QColor(base_color)
                    c_old.setAlphaF(max(0.0, 1.0 - eased))
                    painter.setPen(c_old)
                    w_a = metrics.horizontalAdvance(a)
                    painter.drawText(QPointF(cur_x + (col_w - w_a) / 2.0, base_y - slide_h * eased), a)
                if progress > 0.0 and b != " ":
                    c_new = QColor(base_color)
                    c_new.setAlphaF(min(1.0, eased))
                    painter.setPen(c_new)
                    w_b = metrics.horizontalAdvance(b)
                    painter.drawText(QPointF(cur_x + (col_w - w_b) / 2.0, base_y + slide_h * (1.0 - eased)), b)

            cur_x += col_w

        painter.restore()

        if progress >= 1.0:
            self.transition_from = self.transition_to

    def enterEvent(self, event):
        self.is_hovered = True
        self.update()
        super().enterEvent(event)

    def leaveEvent(self, event):
        self.is_hovered = False
        self.hover_pos = None
        self.update()
        super().leaveEvent(event)

    def mousePressEvent(self, event: QMouseEvent):
        if event.button() == Qt.MouseButton.LeftButton:
            self.drag_start_global = event.globalPosition().toPoint()
            self.drag_start_win = self.pos()
            self.is_dragging = False
        super().mousePressEvent(event)

    def mouseMoveEvent(self, event: QMouseEvent):
        self.hover_pos = event.position()
        if (event.buttons() & Qt.MouseButton.LeftButton) and self.drag_start_global is not None:
            delta = event.globalPosition().toPoint() - self.drag_start_global
            if not self.config_manager.locked and (self.is_dragging or delta.manhattanLength() > 5):
                if not self.is_dragging:
                    refresh_screen_cache(exclude_rects=[(self.x(), self.y(), self.width(), self.height())])
                self.is_dragging = True
                new_pos = self.drag_start_win + delta

                info = self.get_taskbar_geometry()
                if info:
                    tb_left, tb_top, tb_right, tb_bottom = info["tb_rect"]
                    tb_w = tb_right - tb_left
                    tb_h = tb_bottom - tb_top
                    if tb_w >= tb_h:
                        tb_center_y = tb_top + (tb_h - self.capsule_height) // 2
                        if abs(new_pos.y() - tb_center_y) < 22:
                            new_pos.setY(tb_center_y)

                self.move(new_pos)
                # Real-time SDF Liquid Glass refraction while dragging!
                self.refresh_backdrop()
        else:
            self.update()
        super().mouseMoveEvent(event)

    def mouseReleaseEvent(self, event: QMouseEvent):
        if event.button() == Qt.MouseButton.LeftButton:
            if not self.is_dragging:
                self.clicked.emit()
            else:
                info = self.get_taskbar_geometry()
                if info:
                    tb_left, tb_top, tb_right, tb_bottom = info["tb_rect"]
                    tb_w = tb_right - tb_left
                    tb_h = tb_bottom - tb_top
                    tray_rect = info["tray_rect"]
                    if tb_w >= tb_h:
                        tb_center_y = tb_top + (tb_h - self.capsule_height) // 2
                        if abs(self.y() - tb_center_y) <= 24:
                            self.move(self.x(), tb_center_y)
                            if tray_rect and abs((self.x() + self.capsule_width) - tray_rect[0]) < 36:
                                self.config_manager.dock_mode = "tray_left"
                                self.config_manager.docked = True
                                self.config_manager.custom_ratio = None
                            else:
                                self.config_manager.dock_mode = "custom"
                                self.config_manager.docked = True
                                span = max(1, tb_w - self.capsule_width)
                                self.config_manager.custom_ratio = max(0.0, min(1.0, (self.x() - tb_left) / span))
                        else:
                            self.config_manager.dock_mode = "custom"
                            self.config_manager.docked = False
                            self.config_manager.custom_ratio = None
                self.config_manager.capsule_pos = (self.x(), self.y())
                self.config_manager.save()
                self.adapt_position(force=True)
            self.is_dragging = False
            self.drag_start_global = None
            self.drag_start_win = None
        elif event.button() == Qt.MouseButton.RightButton:
            self.show_context_menu(event.globalPosition().toPoint())
        super().mouseReleaseEvent(event)

    def set_dock_mode(self, mode: str):
        self.config_manager.dock_mode = mode
        self.config_manager.docked = True
        if mode != "custom":
            self.config_manager.custom_ratio = None
        self.adapt_position(force=True)
        self.config_manager.save()

    def show_context_menu(self, pos: QPoint):
        menu = QMenu(self)
        theme = get_theme_colors()

        bg = "#1C1917" if theme.is_dark else "#FAF9F6"
        fg = "#FAF9F6" if theme.is_dark else "#1A1A1A"
        hl_bg = "rgba(212, 175, 55, 0.22)" if theme.is_dark else "rgba(197, 160, 89, 0.18)"
        border_c = "rgba(212, 175, 55, 0.40)" if theme.is_dark else "#C5A059"

        menu.setStyleSheet(f"""
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

        action_details = menu.addAction("今日收入详情…")
        action_details.triggered.connect(self.clicked.emit)

        action_settings = menu.addAction("修改工资与作息…")
        action_settings.triggered.connect(self.request_settings.emit)

        menu.addSeparator()

        pos_menu = menu.addMenu("状态栏自适应位置")
        pos_menu.setStyleSheet(menu.styleSheet())
        cur_mode = getattr(self.config_manager, "dock_mode", "tray_left")

        modes = [
            ("tray_left", "自动跟随托盘左侧 (推荐)"),
            ("apps_right", "自动跟随应用图标右侧"),
            ("taskbar_center", "状态栏居中对齐"),
        ]
        for mode_key, mode_label in modes:
            act = pos_menu.addAction(mode_label)
            act.setCheckable(True)
            act.setChecked(cur_mode == mode_key)
            act.triggered.connect(lambda checked, m=mode_key: self.set_dock_mode(m))

        lock_text = "解锁位置 (允许自由拖动)" if self.config_manager.locked else "锁定当前位置"
        action_lock = menu.addAction(lock_text)
        action_lock.triggered.connect(self.toggle_lock)

        action_autostart = menu.addAction("开机自动启动")
        action_autostart.setCheckable(True)
        action_autostart.setChecked(self.config_manager.is_autostart_enabled())
        action_autostart.toggled.connect(self.autostart_toggled.emit)

        menu.addSeparator()

        action_quit = menu.addAction("退出今日收入")
        action_quit.triggered.connect(self.request_quit.emit)

        menu.exec(pos)

    def action_dock_triggered(self):
        self.set_dock_mode("tray_left")

    def toggle_lock(self):
        self.config_manager.locked = not self.config_manager.locked
        self.config_manager.save()
