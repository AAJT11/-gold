from __future__ import annotations
import time
import math
from datetime import datetime
from typing import Optional, Tuple
from PyQt6.QtCore import Qt, QPoint, QPointF, QRect, QRectF, pyqtSignal, QEvent, QTimer
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton, QMenu,
    QGraphicsDropShadowEffect, QFrame, QApplication
)
from PyQt6.QtGui import (
    QPainter, QColor, QFont, QFontMetrics, QPen, QBrush, QLinearGradient,
    QPainterPath, QKeyEvent, QMouseEvent, QPixmap
)
import win32gui
import win32con

from income_model import IncomeSettings, IncomeSnapshot, Status
from theme import (
    get_theme_colors, is_apps_dark_mode,
    capture_frosted_backdrop, paint_native_popover_card, refresh_screen_cache
)


class NativeSlimProgressBar(QWidget):
    """3px ultra-clean capsule progress bar matching macOS native."""
    def __init__(self, parent=None):
        super().__init__(parent)
        self.progress: float = 0.0
        self.status: Status = Status.WORKING
        self.is_million: bool = False
        self.setFixedHeight(4)

    def set_progress(self, p: float, status: Status = Status.WORKING, is_million: bool = False):
        self.progress = max(0.0, min(1.0, p))
        self.status = status
        self.is_million = is_million
        self.update()

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing, True)

        is_dark = is_apps_dark_mode()
        rect = QRectF(0.0, 0.5, self.width(), self.height() - 1.0)
        radius = rect.height() / 2.0

        # Subtle track
        track_bg = QColor(255, 255, 255, 30) if is_dark else QColor(0, 0, 0, 16)
        painter.setPen(Qt.PenStyle.NoPen)
        painter.setBrush(QBrush(track_bg))
        painter.drawRoundedRect(rect, radius, radius)

        if self.progress > 0:
            fill_w = max(rect.height(), rect.width() * self.progress)
            fill_rect = QRectF(rect.left(), rect.top(), fill_w, rect.height())

            if self.is_million:
                grad = QLinearGradient(fill_rect.topLeft(), fill_rect.topRight())
                grad.setColorAt(0.0, QColor("#F59E0B"))
                grad.setColorAt(1.0, QColor("#FBBF24"))
                painter.setBrush(QBrush(grad))
            else:
                fill_col = QColor(229, 231, 235) if is_dark else QColor(55, 65, 81, 220)
                painter.setBrush(QBrush(fill_col))

            painter.drawRoundedRect(fill_rect, radius, radius)


class NativePopoverCard(QFrame):
    """Frosted Glass popover card with top beak arrow pointing to capsule."""
    def __init__(self, parent=None):
        super().__init__(parent)
        self.backdrop: Optional[QPixmap] = None
        self.arrow_dir: str = "top"  # 'top' or 'bottom'
        self.arrow_x: Optional[float] = None
        self.setMouseTracking(True)

    def paintEvent(self, event):
        painter = QPainter(self)
        rect = QRectF(0.0, 0.0, self.width(), self.height())
        paint_native_popover_card(
            painter=painter,
            rect=rect,
            radius=18.0,
            is_dark=is_apps_dark_mode(),
            backdrop=self.backdrop,
            arrow_dir=self.arrow_dir,
            arrow_x=self.arrow_x,
            arrow_w=18.0,
            arrow_h=9.0
        )

LiquidGlassCard = NativePopoverCard

class LiquidSubPanel(QFrame):
    def __init__(self, parent=None):
        super().__init__(parent)


class NativeRollingAmountLabel(QWidget):
    """Crisp, native system-font money label with smooth rolling & fast thousandths ticker."""
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setFixedHeight(50)
        self.amount_text = "¥0.00"
        self.transition_from = ""
        self.transition_to = ""
        self.transition_start_time = 0.0
        self.duration = 0.8
        self.is_working = False

        self.timer = QTimer(self)
        self.timer.timeout.connect(self.update)

    def set_amount(self, text: str, is_working: bool):
        now = time.monotonic()
        if not self.transition_to or not is_working:
            self.transition_from = text
            self.transition_to = text
            self.transition_start_time = now - self.duration
        elif text != self.transition_to:
            self.transition_from = self.transition_to
            self.transition_to = text
            self.transition_start_time = now

        self.amount_text = text
        self.is_working = is_working

        if is_working and self.isVisible():
            if not self.timer.isActive():
                self.timer.start(20)
        else:
            if self.timer.isActive():
                self.timer.stop()
            self.update()

    def showEvent(self, event):
        super().showEvent(event)
        if self.is_working and not self.timer.isActive():
            self.timer.start(20)

    def hideEvent(self, event):
        if self.timer.isActive():
            self.timer.stop()
        super().hideEvent(event)

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing, True)
        painter.setRenderHint(QPainter.RenderHint.TextAntialiasing, True)

        is_dark = is_apps_dark_mode()
        font = QFont("Segoe UI Variable Display", 32, QFont.Weight.Bold)
        font.setStyleHint(QFont.StyleHint.SansSerif)
        font.setLetterSpacing(QFont.SpacingType.AbsoluteSpacing, -0.6)
        painter.setFont(font)
        metrics = QFontMetrics(font)

        now = time.monotonic()
        progress = min(1.0, max(0.0, (now - self.transition_start_time) / self.duration))
        eased = progress * progress * (3.0 - 2.0 * progress)
        fast_phase = (now * 12.0) if self.is_working else None

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

        clip_rect = QRectF(0.0, 0.0, self.width(), self.height())
        painter.save()
        painter.setClipRect(clip_rect)

        ascent = metrics.ascent()
        descent = metrics.descent()
        base_y = self.height() / 2.0 + (ascent - descent) / 2.0
        slide_h = 32.0

        cur_x = 0.0
        base_color = QColor("#F9FAFB") if is_dark else QColor("#111827")

        for i in range(count):
            a = prev_chars[i]
            b = next_chars[i]
            col_w = widths[i]

            if i == count - 1 and fast_phase is not None:
                base_val = int(math.floor(fast_phase))
                fraction = fast_phase - base_val
                g1 = str(base_val % 10)
                g2 = str((base_val + 1) % 10)
                w1 = metrics.horizontalAdvance(g1)
                w2 = metrics.horizontalAdvance(g2)

                painter.setPen(base_color)
                painter.drawText(QPointF(cur_x + (col_w - w1) / 2.0, base_y - slide_h * fraction), g1)
                painter.drawText(QPointF(cur_x + (col_w - w2) / 2.0, base_y + slide_h * (1.0 - fraction)), g2)

            elif a == b:
                if b != " ":
                    painter.setPen(base_color)
                    wb = metrics.horizontalAdvance(b)
                    painter.drawText(QPointF(cur_x + (col_w - wb) / 2.0, base_y), b)
            else:
                if progress < 1.0 and a != " ":
                    c_old = QColor(base_color)
                    c_old.setAlphaF(max(0.0, 1.0 - eased))
                    painter.setPen(c_old)
                    wa = metrics.horizontalAdvance(a)
                    painter.drawText(QPointF(cur_x + (col_w - wa) / 2.0, base_y - slide_h * eased), a)
                if progress > 0.0 and b != " ":
                    c_new = QColor(base_color)
                    c_new.setAlphaF(min(1.0, eased))
                    painter.setPen(c_new)
                    wb = metrics.horizontalAdvance(b)
                    painter.drawText(QPointF(cur_x + (col_w - wb) / 2.0, base_y + slide_h * (1.0 - eased)), b)

            cur_x += col_w

        painter.restore()

        if progress >= 1.0:
            self.transition_from = self.transition_to


class IncomeDetailsPopover(QWidget):
    request_settings = pyqtSignal()
    request_quit = pyqtSignal()
    autostart_toggled = pyqtSignal(bool)
    request_dock = pyqtSignal()
    million_mode_toggled = pyqtSignal(bool)

    def __init__(self, config_manager, parent=None):
        super().__init__(parent)
        self.config_manager = config_manager
        self.settings: IncomeSettings = config_manager.settings
        self.snapshot: Optional[IncomeSnapshot] = None
        self.is_menu_open = False
        self.last_hide_time: float = 0.0
        self.drag_offset: Optional[QPoint] = None
        self._last_capture_time: float = 0.0

        self.setWindowTitle("今日收入")
        self.setWindowFlags(
            Qt.WindowType.FramelessWindowHint |
            Qt.WindowType.WindowStaysOnTopHint |
            Qt.WindowType.Tool
        )
        self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground, True)
        self.setMouseTracking(True)
        self.setFixedWidth(318)

        self.init_ui()

    def changeEvent(self, event):
        if event.type() == QEvent.Type.ActivationChange:
            if not self.isActiveWindow() and not self.is_menu_open:
                self.hide()
        super().changeEvent(event)

    def hideEvent(self, event):
        self.last_hide_time = time.monotonic()
        super().hideEvent(event)

    def showEvent(self, event):
        super().showEvent(event)
        try:
            hwnd = int(self.winId())
            win32gui.SetWindowPos(
                hwnd,
                win32con.HWND_TOPMOST,
                0, 0, 0, 0,
                win32con.SWP_NOMOVE | win32con.SWP_NOSIZE
            )
        except Exception:
            pass
        self.refresh_backdrop(force=True)

    def refresh_backdrop(self, force: bool = False):
        now = time.monotonic()
        if not force and (now - self._last_capture_time < 0.025):
            return
        self._last_capture_time = now
        card_global = self.mapToGlobal(self.card.pos())
        w = max(280, self.card.width())
        h = max(240, self.card.height())
        self.card.backdrop = capture_frosted_backdrop(
            card_global.x(),
            card_global.y(),
            w,
            h,
            blur_radius=26.0
        )
        self.card.update()

    def init_ui(self):
        outer_layout = QVBoxLayout(self)
        outer_layout.setContentsMargins(10, 10, 10, 10)

        self.card = NativePopoverCard(self)
        self.card.setObjectName("PopoverCard")
        outer_layout.addWidget(self.card)

        # Soft floating drop shadow
        shadow = QGraphicsDropShadowEffect(self)
        shadow.setBlurRadius(24)
        shadow.setColor(QColor(0, 0, 0, 45))
        shadow.setOffset(0, 6)
        self.card.setGraphicsEffect(shadow)

        self.card_layout = QVBoxLayout(self.card)
        self.card_layout.setContentsMargins(20, 22, 20, 18)
        self.card_layout.setSpacing(12)

        # 1. Header (Title + Status Pill)
        header_layout = QHBoxLayout()
        header_layout.setContentsMargins(0, 0, 0, 0)

        self.title_label = QLabel("今日收入", self.card)
        self.title_label.setFont(QFont("Microsoft YaHei UI", 11, QFont.Weight.Bold))

        self.status_label = QLabel("正在赚钱", self.card)
        self.status_label.setFont(QFont("Microsoft YaHei UI", 9, QFont.Weight.Medium))

        header_layout.addWidget(self.title_label)
        header_layout.addStretch()
        header_layout.addWidget(self.status_label, alignment=Qt.AlignmentFlag.AlignVCenter)
        self.card_layout.addLayout(header_layout)

        # 2. Big Money Amount Label
        self.amount_label = NativeRollingAmountLabel(self.card)
        self.card_layout.addWidget(self.amount_label)

        # 3. Slim Progress Bar & Info Sub-line
        prog_layout = QVBoxLayout()
        prog_layout.setSpacing(6)
        self.progress_bar = NativeSlimProgressBar(self.card)
        prog_layout.addWidget(self.progress_bar)

        prog_sub_layout = QHBoxLayout()
        self.timing_label = QLabel("按工作时间自动计薪", self.card)
        self.timing_label.setFont(QFont("Microsoft YaHei UI", 8, QFont.Weight.Normal))
        self.percent_label = QLabel("0%", self.card)
        self.percent_label.setFont(QFont("Segoe UI Variable Display", 8, QFont.Weight.Bold))
        prog_sub_layout.addWidget(self.timing_label)
        prog_sub_layout.addStretch()
        prog_sub_layout.addWidget(self.percent_label)
        prog_layout.addLayout(prog_sub_layout)

        self.card_layout.addLayout(prog_layout)

        # 4. Subtle Hairline Divider
        self.divider1 = QFrame(self.card)
        self.divider1.setFrameShape(QFrame.Shape.HLine)
        self.divider1.setFixedHeight(1)
        self.card_layout.addWidget(self.divider1)

        # 5. Clean Details Rows (macOS native style)
        self.details_layout = QVBoxLayout()
        self.details_layout.setContentsMargins(0, 2, 0, 2)
        self.details_layout.setSpacing(9)
        self.detail_labels = []

        self.val_hours = self.add_detail_row("工作时间", "09:00–18:00")
        self.val_lunch = self.add_detail_row("午休", "12:30–14:00 · 不计薪")
        self.lbl_salary, self.val_monthly = self.add_detail_row_with_label("月薪", "¥5,000.00")
        self.val_daily = self.add_detail_row("日薪估算", "¥192.31")
        self.val_hourly = self.add_detail_row("每小时", "¥21.37")
        self.card_layout.addLayout(self.details_layout)

        # 6. 「百万年薪体验」Gold Gradient Pill Button
        million_box = QVBoxLayout()
        million_box.setSpacing(4)
        million_box.setContentsMargins(0, 4, 0, 2)

        self.btn_million = QPushButton("✨ 体验百万年薪", self.card)
        self.btn_million.setObjectName("MillionButton")
        self.btn_million.setCursor(Qt.CursorShape.PointingHandCursor)
        self.btn_million.setFixedHeight(36)
        self.btn_million.setFont(QFont("Microsoft YaHei UI", 9, QFont.Weight.Bold))
        self.btn_million.clicked.connect(self.toggle_million_mode)

        self.lbl_million_caption = QLabel("体验金额 · 年薪 100 万 ÷ 12，退出后恢复真实工资", self.card)
        self.lbl_million_caption.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.lbl_million_caption.setFont(QFont("Microsoft YaHei UI", 7, QFont.Weight.Normal))

        million_box.addWidget(self.btn_million)
        million_box.addWidget(self.lbl_million_caption)
        self.card_layout.addLayout(million_box)

        # 7. Subtle Hairline Divider 2
        self.divider2 = QFrame(self.card)
        self.divider2.setFrameShape(QFrame.Shape.HLine)
        self.divider2.setFixedHeight(1)
        self.card_layout.addWidget(self.divider2)

        # 8. Footer: Settings & More Options
        footer_layout = QHBoxLayout()
        footer_layout.setContentsMargins(0, 0, 0, 0)

        self.btn_edit = QPushButton("修改设置", self.card)
        self.btn_edit.setObjectName("SettingsTextButton")
        self.btn_edit.setFont(QFont("Microsoft YaHei UI", 8, QFont.Weight.Medium))
        self.btn_edit.setCursor(Qt.CursorShape.PointingHandCursor)
        self.btn_edit.clicked.connect(self.on_edit_clicked)

        self.btn_more = QPushButton("••• ⌵", self.card)
        self.btn_more.setObjectName("MoreTextButton")
        self.btn_more.setFont(QFont("Segoe UI", 9, QFont.Weight.Medium))
        self.btn_more.setCursor(Qt.CursorShape.PointingHandCursor)
        self.btn_more.clicked.connect(self.show_more_menu)

        footer_layout.addWidget(self.btn_edit)
        footer_layout.addStretch()
        footer_layout.addWidget(self.btn_more)
        self.card_layout.addLayout(footer_layout)

        self.update_styles()

    def add_detail_row(self, label_text: str, default_val: str) -> QLabel:
        _, val = self.add_detail_row_with_label(label_text, default_val)
        return val

    def add_detail_row_with_label(self, label_text: str, default_val: str) -> Tuple[QLabel, QLabel]:
        row = QHBoxLayout()
        row.setContentsMargins(0, 0, 0, 0)
        lbl = QLabel(label_text, self.card)
        lbl.setFont(QFont("Microsoft YaHei UI", 9, QFont.Weight.Normal))
        val = QLabel(default_val, self.card)
        val.setFont(QFont("Segoe UI Variable Display", 9, QFont.Weight.DemiBold))
        row.addWidget(lbl)
        row.addStretch()
        row.addWidget(val)
        self.details_layout.addLayout(row)
        self.detail_labels.append((lbl, val))
        return lbl, val

    def toggle_million_mode(self):
        is_active = not self.config_manager.million_mode
        self.config_manager.million_mode = is_active
        self.million_mode_toggled.emit(is_active)
        self.update_styles()
        # Force immediate re-render of labels
        if self.snapshot and self.settings:
            now = datetime.now() if 'datetime' in globals() else time.time()
            eff_settings = self.config_manager.effective_settings
            from income_model import IncomeMath
            new_snap = IncomeMath.snapshot(eff_settings, datetime.now())
            self.update_data(new_snap, eff_settings, datetime.now())

    def update_styles(self):
        is_dark = is_apps_dark_mode()
        is_million = getattr(self.config_manager, "million_mode", False)

        fg_pri = "#F9FAFB" if is_dark else "#111827"
        fg_sec = "#9CA3AF" if is_dark else "#6B7280"
        divider_col = "rgba(255, 255, 255, 0.08)" if is_dark else "rgba(0, 0, 0, 0.07)"

        # Title Label
        if is_million:
            self.title_label.setText("百万年薪体验")
            self.lbl_salary.setText("体验月薪")
            self.btn_million.setText("↶ 退出百万年薪体验")
        else:
            self.title_label.setText("今日收入")
            self.lbl_salary.setText("月薪")
            self.btn_million.setText("✨ 体验百万年薪")

        # Status badge styling (clean subtle grey pill)
        status = self.snapshot.status if self.snapshot else Status.WORKING
        badge_bg = "rgba(255, 255, 255, 0.10)" if is_dark else "rgba(0, 0, 0, 0.05)"
        badge_fg = "#D1D5DB" if is_dark else "#4B5563"

        self.status_label.setStyleSheet(f"""
            color: {badge_fg};
            background-color: {badge_bg};
            border-radius: 4px;
            padding: 2px 7px;
        """)

        # Dividers
        self.divider1.setStyleSheet(f"background-color: {divider_col}; border: none;")
        self.divider2.setStyleSheet(f"background-color: {divider_col}; border: none;")

        # General text labels
        self.title_label.setStyleSheet(f"color: {fg_pri}; background: transparent;")
        self.timing_label.setStyleSheet(f"color: {fg_sec}; background: transparent;")
        self.percent_label.setStyleSheet(f"color: {fg_sec}; background: transparent;")
        self.lbl_million_caption.setStyleSheet("color: #9CA3AF; background: transparent;")

        for lbl, val in self.detail_labels:
            lbl.setStyleSheet(f"color: {fg_sec}; background: transparent;")
            val.setStyleSheet(f"color: {fg_pri}; background: transparent;")

        # Million Pill Button: Golden Yellow Gradient
        self.btn_million.setStyleSheet("""
            QPushButton#MillionButton {
                background: qlineargradient(x1:0, y1:0, x2:1, y2:1, stop:0 #F59E0B, stop:0.55 #FBBF24, stop:1 #EAB308);
                color: #78350F;
                border: none;
                border-radius: 18px;
                font-weight: bold;
                font-size: 13px;
                padding: 6px 16px;
            }
            QPushButton#MillionButton:hover {
                background: qlineargradient(x1:0, y1:0, x2:1, y2:1, stop:0 #FBBF24, stop:0.55 #FCD34D, stop:1 #F59E0B);
                color: #451A03;
            }
            QPushButton#MillionButton:pressed {
                background-color: #D97706;
            }
        """)

        # Footer Text Buttons
        btn_fg = "#9CA3AF" if is_dark else "#4B5563"
        btn_hover = "#F3F4F6" if is_dark else "#111827"
        self.btn_edit.setStyleSheet(f"""
            QPushButton#SettingsTextButton {{
                color: {btn_fg};
                background: transparent;
                border: none;
                padding: 2px 4px;
            }}
            QPushButton#SettingsTextButton:hover {{
                color: {btn_hover};
            }}
        """)
        self.btn_more.setStyleSheet(f"""
            QPushButton#MoreTextButton {{
                color: {btn_fg};
                background: transparent;
                border: none;
                padding: 2px 4px;
            }}
            QPushButton#MoreTextButton:hover {{
                color: {btn_hover};
            }}
        """)

    def update_data(self, snapshot: IncomeSnapshot, settings: IncomeSettings, now):
        self.snapshot = snapshot
        self.settings = settings
        is_million = getattr(self.config_manager, "million_mode", False)

        is_working = (snapshot.status == Status.WORKING)
        formatted_money = settings.money(snapshot.earned, digits=3 if is_working else 2)
        self.amount_label.set_amount(formatted_money, is_working)
        self.status_label.setText(snapshot.status_label)
        self.progress_bar.set_progress(snapshot.progress, snapshot.status, is_million=is_million)
        self.timing_label.setText(snapshot.timing_label(now))
        self.percent_label.setText(f"{int(round(snapshot.progress * 100))}%")

        self.val_hours.setText(settings.hours_label)
        self.val_lunch.setText(f"{settings.lunch_label} · 不计薪" if settings.has_lunch_break else "无午休")
        self.val_monthly.setText(settings.money(settings.monthly_salary))
        self.val_daily.setText(settings.money(settings.daily_salary))
        self.val_hourly.setText(settings.money(settings.hourly_salary))
        self.update_styles()

    def mousePressEvent(self, event: QMouseEvent):
        if event.button() == Qt.MouseButton.LeftButton:
            self.drag_offset = event.globalPosition().toPoint() - self.pos()
        super().mousePressEvent(event)

    def mouseMoveEvent(self, event: QMouseEvent):
        if (event.buttons() & Qt.MouseButton.LeftButton) and self.drag_offset is not None:
            self.move(event.globalPosition().toPoint() - self.drag_offset)
        super().mouseMoveEvent(event)

    def mouseReleaseEvent(self, event: QMouseEvent):
        self.drag_offset = None
        super().mouseReleaseEvent(event)

    def on_edit_clicked(self):
        self.hide()
        self.request_settings.emit()

    def show_more_menu(self):
        menu = QMenu(self)
        is_dark = is_apps_dark_mode()
        menu_bg = "#222222" if is_dark else "#FFFFFF"
        menu_fg = "#F9FAFB" if is_dark else "#111827"
        menu_hover = "#333333" if is_dark else "#F3F4F6"
        menu.setStyleSheet(f"""
            QMenu {{
                background-color: {menu_bg};
                color: {menu_fg};
                border: 1px solid {"rgba(255,255,255,0.12)" if is_dark else "rgba(0,0,0,0.08)"};
                border-radius: 8px;
                padding: 4px;
            }}
            QMenu::item {{
                padding: 6px 18px;
                border-radius: 4px;
            }}
            QMenu::item:selected {{
                background-color: {menu_hover};
            }}
        """)

        is_auto = self.config_manager.is_autostart_enabled()
        act_auto = menu.addAction("关闭开机启动" if is_auto else "开启开机启动")
        act_auto.triggered.connect(lambda: self.autostart_toggled.emit(not is_auto))

        menu.addSeparator()

        act_dock = menu.addAction("吸附并固定到状态栏")
        act_dock.triggered.connect(self.request_dock.emit)

        menu.addSeparator()

        is_million = getattr(self.config_manager, "million_mode", False)
        act_million = menu.addAction("退出百万年薪体验" if is_million else "开启百万年薪体验")
        act_million.triggered.connect(self.toggle_million_mode)

        menu.addSeparator()

        act_quit = menu.addAction("退出今日收入")
        act_quit.triggered.connect(self.request_quit.emit)

        self.is_menu_open = True
        menu.exec(self.btn_more.mapToGlobal(QPoint(0, self.btn_more.height() + 4)))
        self.is_menu_open = False

    def position_near(self, anchor_rect: QRect):
        self.adjustSize()
        screen = QApplication.primaryScreen().geometry()

        shell = win32gui.FindWindow("Shell_TrayWnd", None)
        tb_rect = win32gui.GetWindowRect(shell) if shell else None

        x = anchor_rect.center().x() - self.width() // 2
        arrow_dir = "top"
        y = anchor_rect.bottom() + 4

        if tb_rect:
            tb_l, tb_t, tb_r, tb_b = tb_rect
            tb_w = tb_r - tb_l
            tb_h = tb_b - tb_t
            if tb_w >= tb_h:
                if tb_b <= screen.height() // 2:
                    y = max(tb_b, anchor_rect.bottom()) + 2
                    arrow_dir = "top"
                else:
                    y = min(tb_t, anchor_rect.top()) - self.height() - 2
                    arrow_dir = "bottom"
            else:
                y = anchor_rect.center().y() - self.height() // 2
                arrow_dir = "top"
        else:
            if anchor_rect.bottom() > screen.height() // 2:
                y = anchor_rect.top() - self.height() - 2
                arrow_dir = "bottom"
            else:
                y = anchor_rect.bottom() + 2
                arrow_dir = "top"

        x = max(screen.left() + 8, min(screen.right() - self.width() - 8, x))
        y = max(screen.top() + 6, min(screen.bottom() - self.height() - 8, y))

        self.card.arrow_dir = arrow_dir
        # Calculate arrow peak location relative to the card's local coordinates
        arrow_screen_x = anchor_rect.center().x()
        self.card.arrow_x = float(arrow_screen_x - (x + self.card.x()))

        # Adjust content margins according to arrow orientation
        if arrow_dir == "top":
            self.card_layout.setContentsMargins(20, 22, 20, 16)
        else:
            self.card_layout.setContentsMargins(20, 16, 20, 22)

        self.move(x, y)
        if not self.isVisible():
            refresh_screen_cache()
        self.refresh_backdrop(force=True)

    def keyPressEvent(self, event: QKeyEvent):
        if event.key() == Qt.Key.Key_Escape:
            self.hide()
        super().keyPressEvent(event)
