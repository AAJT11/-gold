from __future__ import annotations
import time
import math
from typing import Optional
from PyQt6.QtCore import Qt, QPoint, QPointF, QRect, QRectF, pyqtSignal, QEvent, QTimer
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton, QMenu,
    QGraphicsDropShadowEffect, QFrame, QApplication
)
from PyQt6.QtGui import (
    QPainter, QColor, QFont, QPen, QBrush, QLinearGradient,
    QPainterPath, QKeyEvent, QMouseEvent, QPixmap
)
import win32gui
import win32con

from income_model import IncomeSettings, IncomeSnapshot, Status
from theme import (
    get_theme_colors, is_apps_dark_mode,
    capture_frosted_backdrop, paint_liquid_glass_panel, refresh_screen_cache
)

class LiquidGlassProgressBar(QWidget):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.progress: float = 0.0
        self.status: Status = Status.WORKING
        self.setFixedHeight(7)

    def set_progress(self, p: float, status: Status = Status.WORKING):
        self.progress = max(0.0, min(1.0, p))
        self.status = status
        self.update()

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing, True)

        is_dark = is_apps_dark_mode()
        rect = QRectF(0.5, 0.5, self.width() - 1.0, self.height() - 1.0)
        radius = rect.height() / 2.0

        # Warm Stone Track (--border: #E5E5E5 / #E7E5E4)
        track_bg = QColor(255, 255, 255, 28) if is_dark else QColor(229, 226, 220, 255)
        painter.setPen(Qt.PenStyle.NoPen)
        painter.setBrush(QBrush(track_bg))
        painter.drawRoundedRect(rect, radius, radius)

        if self.progress > 0:
            fill_w = max(rect.height(), rect.width() * self.progress)
            fill_rect = QRectF(rect.left(), rect.top(), fill_w, rect.height())
            # .gold-gradient { background: linear-gradient(135deg, gold, orange, gold) } refined for luxury UI
            grad = QLinearGradient(fill_rect.topLeft(), fill_rect.topRight())
            grad.setColorAt(0.0, QColor("#A16207"))
            grad.setColorAt(0.45, QColor("#D4AF37"))
            grad.setColorAt(0.75, QColor("#E5C158"))
            grad.setColorAt(1.0, QColor("#B8860B"))

            painter.setPen(Qt.PenStyle.NoPen)
            painter.setBrush(QBrush(grad))
            painter.drawRoundedRect(fill_rect, radius, radius)


class LiquidGlassCard(QFrame):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.backdrop: Optional[QPixmap] = None
        self.hover_pos: Optional[QPointF] = None
        self.setMouseTracking(True)

    def paintEvent(self, event):
        painter = QPainter(self)
        rect = QRectF(1.0, 1.0, self.width() - 2.0, self.height() - 2.0)
        paint_liquid_glass_panel(
            painter=painter,
            rect=rect,
            radius=16.0,
            is_dark=is_apps_dark_mode(),
            backdrop=self.backdrop,
            hover_pos=self.hover_pos
        )


class LiquidSubPanel(QFrame):
    def __init__(self, parent=None):
        super().__init__(parent)

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing, True)
        is_dark = is_apps_dark_mode()
        rect = QRectF(0.5, 0.5, self.width() - 1.0, self.height() - 1.0)
        radius = 10.0

        # Crisp Ivory Card (--bg: #FFFFFF) over Warm Alabaster Cream (--bg-cream: #FAF9F6)
        grad = QLinearGradient(rect.topLeft(), rect.bottomLeft())
        if is_dark:
            grad.setColorAt(0.0, QColor(38, 34, 31, 210))
            grad.setColorAt(1.0, QColor(30, 27, 24, 210))
            border_c = QColor(212, 175, 55, 48)
        else:
            grad.setColorAt(0.0, QColor(255, 255, 255, 248))
            grad.setColorAt(1.0, QColor(253, 252, 250, 242))
            border_c = QColor(229, 229, 229, 255)  # --border: #E5E5E5

        painter.setPen(QPen(border_c, 1.0))
        painter.setBrush(QBrush(grad))
        painter.drawRoundedRect(rect, radius, radius)

        # Subtle Champagne Gold left accent line (editorial signature from uupm.cc/demo/luxury-ecommerce)
        gold_accent = QColor(212, 175, 55, 190) if is_dark else QColor(197, 160, 89, 210)
        painter.setPen(Qt.PenStyle.NoPen)
        painter.setBrush(QBrush(gold_accent))
        painter.drawRoundedRect(QRectF(rect.left(), rect.top() + 12, 2.2, rect.height() - 24), 1.0, 1.0)


class LuxuryRollingAmountLabel(QWidget):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setFixedHeight(46)
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
        font = QFont("Times New Roman", 30, QFont.Weight.Bold)
        font.setLetterSpacing(QFont.SpacingType.AbsoluteSpacing, 0.5)
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
        base_color = QColor(250, 249, 246) if is_dark else QColor(26, 26, 26)

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

                gold_c = QColor("#D4AF37") if is_dark else QColor("#A16207")
                painter.setPen(gold_c)
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
        self.setFixedWidth(332)

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
            blur_radius=28.0
        )
        self.card.update()

    def init_ui(self):
        outer_layout = QVBoxLayout(self)
        outer_layout.setContentsMargins(12, 12, 12, 14)

        self.card = LiquidGlassCard(self)
        self.card.setObjectName("Card")
        outer_layout.addWidget(self.card)

        shadow = QGraphicsDropShadowEffect(self)
        shadow.setBlurRadius(28)
        shadow.setColor(QColor(28, 25, 23, 60))
        shadow.setOffset(0, 8)
        self.card.setGraphicsEffect(shadow)

        card_layout = QVBoxLayout(self.card)
        card_layout.setContentsMargins(22, 18, 22, 18)
        card_layout.setSpacing(12)

        # Editorial Header (MAISON Luxury E-Commerce style)
        header_layout = QHBoxLayout()
        header_layout.setContentsMargins(0, 0, 0, 0)
        title_col = QVBoxLayout()
        title_col.setSpacing(2)

        self.kicker_label = QLabel("PRIVATE WEALTH · EXCLUSIVE", self.card)
        kicker_font = QFont("Segoe UI", 7, QFont.Weight.Bold)
        kicker_font.setLetterSpacing(QFont.SpacingType.AbsoluteSpacing, 1.8)
        self.kicker_label.setFont(kicker_font)

        self.title_label = QLabel("今日收入", self.card)
        self.title_label.setFont(QFont("Microsoft YaHei UI", 12, QFont.Weight.Bold))

        title_col.addWidget(self.kicker_label)
        title_col.addWidget(self.title_label)

        self.status_label = QLabel("正在赚钱", self.card)
        self.status_label.setFont(QFont("Microsoft YaHei UI", 9, QFont.Weight.Bold))
        header_layout.addLayout(title_col)
        header_layout.addStretch()
        header_layout.addWidget(self.status_label, alignment=Qt.AlignmentFlag.AlignVCenter)
        card_layout.addLayout(header_layout)

        # Big Money Amount with per-digit rolling & thousandths ticker (macOS parity)
        self.amount_label = LuxuryRollingAmountLabel(self.card)
        card_layout.addWidget(self.amount_label)

        # Progress bar & info
        prog_layout = QVBoxLayout()
        prog_layout.setSpacing(7)
        self.progress_bar = LiquidGlassProgressBar(self.card)
        prog_layout.addWidget(self.progress_bar)

        prog_sub_layout = QHBoxLayout()
        self.timing_label = QLabel("按工作时间自动计薪", self.card)
        self.timing_label.setFont(QFont("Microsoft YaHei UI", 9, QFont.Weight.DemiBold))
        self.percent_label = QLabel("0%", self.card)
        self.percent_label.setFont(QFont("Segoe UI", 9, QFont.Weight.Bold))
        prog_sub_layout.addWidget(self.timing_label)
        prog_sub_layout.addStretch()
        prog_sub_layout.addWidget(self.percent_label)
        prog_layout.addLayout(prog_sub_layout)

        card_layout.addLayout(prog_layout)

        # Sub-Card for Curated Details
        self.sub_panel = LiquidSubPanel(self.card)
        self.details_layout = QVBoxLayout(self.sub_panel)
        self.details_layout.setContentsMargins(16, 12, 16, 12)
        self.details_layout.setSpacing(8)
        self.detail_labels = []

        self.val_hours = self.add_detail_row("工作时间", "09:00–18:00")
        self.val_lunch = self.add_detail_row("午休时间", "12:30–14:00")
        self.val_payday = self.add_detail_row("发薪日", "次月15日")
        self.val_monthly = self.add_detail_row("月薪", "¥5,000.00")
        self.val_daily = self.add_detail_row("日薪估算", "¥192.31")
        self.val_hourly = self.add_detail_row("每小时", "¥25.64")
        card_layout.addWidget(self.sub_panel)

        # Footer Actions (.btn-primary + .btn-outline from uupm.cc/demo/luxury-ecommerce)
        footer_layout = QHBoxLayout()
        footer_layout.setContentsMargins(0, 2, 0, 0)
        self.btn_edit = QPushButton("修改设置", self.card)
        self.btn_edit.setObjectName("PrimaryLuxuryBtn")
        self.btn_edit.setFont(QFont("Microsoft YaHei UI", 9, QFont.Weight.Bold))
        self.btn_edit.setCursor(Qt.CursorShape.PointingHandCursor)
        self.btn_edit.clicked.connect(self.on_edit_clicked)

        self.btn_more = QPushButton("•••", self.card)
        self.btn_more.setObjectName("MoreButton")
        self.btn_more.setFont(QFont("Segoe UI", 10, QFont.Weight.Bold))
        self.btn_more.setCursor(Qt.CursorShape.PointingHandCursor)
        self.btn_more.setFixedSize(38, 32)
        self.btn_more.clicked.connect(self.show_more_menu)

        footer_layout.addWidget(self.btn_edit)
        footer_layout.addStretch()
        footer_layout.addWidget(self.btn_more)
        card_layout.addLayout(footer_layout)

        self.update_styles()

    def add_detail_row(self, label_text: str, default_val: str) -> QLabel:
        row = QHBoxLayout()
        row.setContentsMargins(0, 0, 0, 0)
        lbl = QLabel(label_text, self.sub_panel)
        lbl.setFont(QFont("Microsoft YaHei UI", 9, QFont.Weight.DemiBold))
        val = QLabel(default_val, self.sub_panel)
        val.setFont(QFont("Segoe UI", 10, QFont.Weight.Bold))
        row.addWidget(lbl)
        row.addStretch()
        row.addWidget(val)
        self.details_layout.addLayout(row)
        self.detail_labels.append((lbl, val))
        return val

    def update_styles(self):
        is_dark = is_apps_dark_mode()
        fg_pri = "#FAF9F6" if is_dark else "#1A1A1A"
        fg_sec = "#A8A29E" if is_dark else "#57534E"
        gold_accent = "#D4AF37" if is_dark else "#A16207"

        # Primary CTA (.btn-primary: Obsidian Black #1C1917 + Gold Accent)
        pri_bg = "#D4AF37" if is_dark else "#1C1917"
        pri_fg = "#1C1917" if is_dark else "#FAF9F6"
        pri_border = "#E5C158" if is_dark else "#C5A059"
        pri_hover_bg = "#E5C158" if is_dark else "#292524"
        pri_hover_fg = "#0C0A09" if is_dark else "#E5C158"

        # Secondary Outline Button (.btn-outline)
        out_bg = "rgba(255, 255, 255, 0.06)" if is_dark else "#FFFFFF"
        out_fg = "#FAF9F6" if is_dark else "#1A1A1A"
        out_border = "rgba(212, 175, 55, 0.35)" if is_dark else "#D6D3D1"
        out_hover_bg = "#D4AF37" if is_dark else "#1C1917"
        out_hover_fg = "#1C1917" if is_dark else "#FAF9F6"

        # Luxury Status Badge (Obsidian & Champagne Gold)
        status = self.snapshot.status if self.snapshot else Status.WORKING
        if status == Status.WORKING:
            badge_bg = "#1C1917" if not is_dark else "rgba(212, 175, 55, 0.18)"
            badge_fg = "#E5C158" if not is_dark else "#F3E5AB"
            badge_border = "#C5A059"
        elif status == Status.LUNCH:
            badge_bg = "#FEF3C7" if not is_dark else "rgba(212, 175, 55, 0.22)"
            badge_fg = "#92400E" if not is_dark else "#FDE68A"
            badge_border = "#D4AF37"
        elif status == Status.BEFORE:
            badge_bg = "#FEF3C7" if not is_dark else "rgba(217, 119, 6, 0.20)"
            badge_fg = "#92400E" if not is_dark else "#FBBF24"
            badge_border = "#D97706"
        elif status == Status.FINISHED:
            badge_bg = "#F5F3EF" if not is_dark else "rgba(255, 255, 255, 0.10)"
            badge_fg = "#1C1917" if not is_dark else "#E7E5E4"
            badge_border = "#C5A059"
        else:
            badge_bg = "#F5F5F4" if not is_dark else "rgba(255, 255, 255, 0.08)"
            badge_fg = "#57534E" if not is_dark else "#A8A29E"
            badge_border = "#D6D3D1"

        self.card.setStyleSheet(f"""
            QLabel {{
                color: {fg_pri};
                background: transparent;
            }}
            QPushButton#PrimaryLuxuryBtn {{
                background-color: {pri_bg};
                color: {pri_fg};
                border: 1px solid {pri_border};
                border-radius: 6px;
                padding: 6px 18px;
            }}
            QPushButton#PrimaryLuxuryBtn:hover {{
                background-color: {pri_hover_bg};
                color: {pri_hover_fg};
            }}
            QPushButton#MoreButton {{
                background-color: {out_bg};
                color: {out_fg};
                border: 1px solid {out_border};
                border-radius: 6px;
                padding: 0px;
            }}
            QPushButton#MoreButton:hover {{
                background-color: {out_hover_bg};
                color: {out_hover_fg};
                border: 1px solid {pri_border};
            }}
        """)
        self.kicker_label.setStyleSheet(f"color: {gold_accent}; background: transparent;")
        self.status_label.setStyleSheet(f"""
            color: {badge_fg};
            background-color: {badge_bg};
            border: 1px solid {badge_border};
            border-radius: 4px;
            padding: 3px 10px;
        """)
        self.timing_label.setStyleSheet(f"color: {fg_sec}; background: transparent;")
        self.percent_label.setStyleSheet(f"color: {gold_accent}; background: transparent;")
        for lbl, val in self.detail_labels:
            lbl.setStyleSheet(f"color: {fg_sec}; background: transparent;")
            val.setStyleSheet(f"color: {fg_pri}; background: transparent;")

    def update_data(self, snapshot: IncomeSnapshot, settings: IncomeSettings, now):
        self.snapshot = snapshot
        self.settings = settings

        is_working = (snapshot.status == Status.WORKING)
        formatted_money = settings.money(snapshot.earned, digits=3 if is_working else 2)
        self.amount_label.set_amount(formatted_money, is_working)
        self.status_label.setText(snapshot.status_label)
        self.progress_bar.set_progress(snapshot.progress, snapshot.status)
        self.timing_label.setText(snapshot.timing_label(now))
        self.percent_label.setText(f"{int(round(snapshot.progress * 100))}%")

        self.val_hours.setText(settings.hours_label)
        self.val_lunch.setText(settings.lunch_label)
        self.val_payday.setText(settings.payday_label(now))
        self.val_monthly.setText(settings.money(settings.monthly_salary))
        self.val_daily.setText(settings.money(settings.daily_salary))
        self.val_hourly.setText(settings.money(settings.hourly_salary))
        self.update_styles()

    def mousePressEvent(self, event: QMouseEvent):
        if event.button() == Qt.MouseButton.LeftButton:
            self.drag_offset = event.globalPosition().toPoint() - self.pos()
        super().mousePressEvent(event)

    def mouseMoveEvent(self, event: QMouseEvent):
        self.card.hover_pos = self.card.mapFrom(self, event.position().toPoint()).toPointF()
        if (event.buttons() & Qt.MouseButton.LeftButton) and self.drag_offset is not None:
            self.move(event.globalPosition().toPoint() - self.drag_offset)
            self.refresh_backdrop(force=False)
        else:
            self.card.update()
        super().mouseMoveEvent(event)

    def mouseReleaseEvent(self, event: QMouseEvent):
        self.drag_offset = None
        super().mouseReleaseEvent(event)

    def leaveEvent(self, event):
        self.card.hover_pos = None
        self.card.update()
        super().leaveEvent(event)

    def on_edit_clicked(self):
        self.hide()
        self.request_settings.emit()

    def show_more_menu(self):
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

        act_autostart = menu.addAction("开机自动启动")
        act_autostart.setCheckable(True)
        act_autostart.setChecked(self.config_manager.is_autostart_enabled())
        act_autostart.toggled.connect(self.autostart_toggled.emit)

        act_dock = menu.addAction("自适应跟随托盘左侧")
        act_dock.triggered.connect(self.request_dock.emit)

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
        y = anchor_rect.bottom() + 4

        if tb_rect:
            tb_l, tb_t, tb_r, tb_b = tb_rect
            tb_w = tb_r - tb_l
            tb_h = tb_b - tb_t
            if tb_w >= tb_h:
                if tb_b <= screen.height() // 2:
                    y = max(tb_b, anchor_rect.bottom()) - 4
                else:
                    y = min(tb_t, anchor_rect.top()) - self.height() + 4
            else:
                y = anchor_rect.center().y() - self.height() // 2
                if tb_r <= screen.width() // 2:
                    x = max(tb_r, anchor_rect.right()) + 4
                else:
                    x = min(tb_l, anchor_rect.left()) - self.width() - 4
        else:
            if anchor_rect.bottom() > screen.height() // 2:
                y = anchor_rect.top() - self.height() + 4
            else:
                y = anchor_rect.bottom() - 4

        x = max(screen.left() + 8, min(screen.right() - self.width() - 8, x))
        y = max(screen.top() + 6, min(screen.bottom() - self.height() - 8, y))

        self.move(x, y)
        if not self.isVisible():
            refresh_screen_cache()
        self.refresh_backdrop(force=True)

    def keyPressEvent(self, event: QKeyEvent):
        if event.key() == Qt.Key.Key_Escape:
            self.hide()
        super().keyPressEvent(event)
