from __future__ import annotations
from typing import Dict, Optional
from PyQt6.QtCore import Qt, QTime, QPoint, QPointF, QRectF, pyqtSignal
from PyQt6.QtWidgets import (
    QDialog, QVBoxLayout, QHBoxLayout, QLabel, QLineEdit, QComboBox,
    QTimeEdit, QCheckBox, QPushButton, QFrame, QGraphicsDropShadowEffect,
    QApplication
)
from PyQt6.QtGui import QFont, QColor, QMouseEvent

import time
from income_model import IncomeSettings, SettingsError
from theme import is_apps_dark_mode, capture_frosted_backdrop, refresh_screen_cache
from ui_popover import LiquidGlassCard, LiquidSubPanel

class SettingsDialog(QDialog):
    saved = pyqtSignal(IncomeSettings)
    dock_mode_changed = pyqtSignal(str)

    def __init__(self, settings: IncomeSettings, current_dock_mode: str = "tray_left", parent=None):
        super().__init__(parent)
        self.original_settings = settings
        self.current_dock_mode = current_dock_mode
        self.drag_offset: Optional[QPoint] = None
        self._last_capture_time: float = 0.0

        self.setWindowTitle("今日收入 · 工资与作息")
        self.setFixedSize(504, 676)
        self.setWindowFlags(
            Qt.WindowType.FramelessWindowHint |
            Qt.WindowType.Dialog |
            Qt.WindowType.WindowStaysOnTopHint
        )
        self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground, True)
        self.setMouseTracking(True)

        self.init_ui()
        self.load_settings(settings, current_dock_mode)
        self.update_styles()
        self.center_and_capture()

    def center_and_capture(self):
        screen = QApplication.primaryScreen().geometry()
        x = screen.left() + (screen.width() - self.width()) // 2
        y = screen.top() + (screen.height() - self.height()) // 2
        self.move(x, y)
        if not self.isVisible():
            refresh_screen_cache()
        self.refresh_backdrop(force=True)

    def showEvent(self, event):
        super().showEvent(event)
        try:
            import win32gui, win32con
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
        w = max(460, self.width() - 28)
        h = max(570, self.height() - 28)
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
        outer_layout.setContentsMargins(14, 14, 14, 14)

        self.card = LiquidGlassCard(self)
        self.card.setObjectName("SettingsCard")
        outer_layout.addWidget(self.card)

        shadow = QGraphicsDropShadowEffect(self)
        shadow.setBlurRadius(28)
        shadow.setColor(QColor(6, 78, 59, 50))
        shadow.setOffset(0, 6)
        self.card.setGraphicsEffect(shadow)

        main_layout = QVBoxLayout(self.card)
        main_layout.setContentsMargins(24, 20, 24, 20)
        main_layout.setSpacing(13)

        # Header Row: Playful Claymorphism
        header_top = QHBoxLayout()
        title_box = QVBoxLayout()
        title_box.setSpacing(2)
        self.kicker_label = QLabel("🌱 快乐成长 · 工资与作息配置", self.card)
        k_font = QFont("Microsoft YaHei UI", 8, QFont.Weight.Bold)
        k_font.setLetterSpacing(QFont.SpacingType.AbsoluteSpacing, 0.8)
        self.kicker_label.setFont(k_font)
        self.title_label = QLabel("工资与作息", self.card)
        self.title_label.setFont(QFont("Microsoft YaHei UI", 15, QFont.Weight.Bold))
        self.subtitle_label = QLabel("设置仅保存在本机。清新薄荷与琥珀蜜糖 3D 软陶拟态视效。", self.card)
        self.subtitle_label.setFont(QFont("Microsoft YaHei UI", 9, QFont.Weight.Medium))
        title_box.addWidget(self.kicker_label)
        title_box.addWidget(self.title_label)
        title_box.addWidget(self.subtitle_label)

        self.btn_close = QPushButton("✕", self.card)
        self.btn_close.setObjectName("CloseButton")
        self.btn_close.setFixedSize(30, 30)
        self.btn_close.setCursor(Qt.CursorShape.PointingHandCursor)
        self.btn_close.clicked.connect(self.reject)

        header_top.addLayout(title_box)
        header_top.addStretch()
        header_top.addWidget(self.btn_close, alignment=Qt.AlignmentFlag.AlignTop)
        main_layout.addLayout(header_top)

        # 1. Form Container (Liquid Sub-Panel)
        self.form_frame = LiquidSubPanel(self.card)
        form_layout = QVBoxLayout(self.form_frame)
        form_layout.setContentsMargins(16, 14, 16, 14)
        form_layout.setSpacing(11)

        # Currency
        row_curr = QHBoxLayout()
        lbl_curr = QLabel("币种", self.form_frame)
        lbl_curr.setFixedWidth(85)
        lbl_curr.setFont(QFont("Microsoft YaHei UI", 9, QFont.Weight.DemiBold))
        self.combo_currency = QComboBox(self.form_frame)
        for code in IncomeSettings.CURRENCIES:
            name = IncomeSettings.CURRENCY_NAMES.get(code, code)
            self.combo_currency.addItem(f"{code} · {name}", code)
        row_curr.addWidget(lbl_curr)
        row_curr.addWidget(self.combo_currency)
        form_layout.addLayout(row_curr)

        # Monthly Salary
        row_salary = QHBoxLayout()
        lbl_sal = QLabel("月薪金额", self.form_frame)
        lbl_sal.setFixedWidth(85)
        lbl_sal.setFont(QFont("Microsoft YaHei UI", 9, QFont.Weight.DemiBold))
        self.edit_salary = QLineEdit(self.form_frame)
        self.edit_salary.setPlaceholderText("例如 5000.00")
        row_salary.addWidget(lbl_sal)
        row_salary.addWidget(self.edit_salary)
        form_layout.addLayout(row_salary)

        # Work Start & End Time
        row_time = QHBoxLayout()
        lbl_start = QLabel("上班时间", self.form_frame)
        lbl_start.setFixedWidth(85)
        lbl_start.setFont(QFont("Microsoft YaHei UI", 9, QFont.Weight.DemiBold))
        self.time_start = QTimeEdit(self.form_frame)
        self.time_start.setDisplayFormat("HH:mm")

        lbl_end = QLabel("下班时间", self.form_frame)
        lbl_end.setFixedWidth(65)
        lbl_end.setFont(QFont("Microsoft YaHei UI", 9, QFont.Weight.DemiBold))
        self.time_end = QTimeEdit(self.form_frame)
        self.time_end.setDisplayFormat("HH:mm")

        row_time.addWidget(lbl_start)
        row_time.addWidget(self.time_start)
        row_time.addSpacing(12)
        row_time.addWidget(lbl_end)
        row_time.addWidget(self.time_end)
        form_layout.addLayout(row_time)

        # Lunch Break Row
        row_lunch = QHBoxLayout()
        self.chk_lunch = QCheckBox("午休不计薪", self.form_frame)
        self.chk_lunch.setFixedWidth(85)
        self.chk_lunch.setFont(QFont("Microsoft YaHei UI", 9, QFont.Weight.DemiBold))
        self.chk_lunch.setCursor(Qt.CursorShape.PointingHandCursor)

        self.time_lunch_start = QTimeEdit(self.form_frame)
        self.time_lunch_start.setDisplayFormat("HH:mm")

        lbl_lunch_to = QLabel("至", self.form_frame)
        lbl_lunch_to.setFixedWidth(65)
        lbl_lunch_to.setAlignment(Qt.AlignmentFlag.AlignCenter)
        lbl_lunch_to.setFont(QFont("Microsoft YaHei UI", 9, QFont.Weight.DemiBold))

        self.time_lunch_end = QTimeEdit(self.form_frame)
        self.time_lunch_end.setDisplayFormat("HH:mm")

        self.chk_lunch.toggled.connect(self.time_lunch_start.setEnabled)
        self.chk_lunch.toggled.connect(self.time_lunch_end.setEnabled)

        row_lunch.addWidget(self.chk_lunch)
        row_lunch.addWidget(self.time_lunch_start)
        row_lunch.addSpacing(12)
        row_lunch.addWidget(lbl_lunch_to)
        row_lunch.addWidget(self.time_lunch_end)
        form_layout.addLayout(row_lunch)

        self.lbl_overnight_hint = QLabel(
            "午休时段自动暂停计薪并从每日有效工时中扣除；币种只改变显示单位。",
            self.form_frame
        )
        self.lbl_overnight_hint.setFont(QFont("Microsoft YaHei UI", 8, QFont.Weight.Medium))
        form_layout.addWidget(self.lbl_overnight_hint)

        main_layout.addWidget(self.form_frame)

        # 2. Rules & Adaptive Position Container (Liquid Sub-Panel)
        self.rules_frame = LiquidSubPanel(self.card)
        rules_layout = QVBoxLayout(self.rules_frame)
        rules_layout.setContentsMargins(16, 14, 16, 14)
        rules_layout.setSpacing(10)

        rules_title = QLabel("计薪规则与状态栏位置", self.rules_frame)
        rules_title.setFont(QFont("Microsoft YaHei UI", 10, QFont.Weight.Bold))
        rules_layout.addWidget(rules_title)

        row_days = QHBoxLayout()
        lbl_days = QLabel("每月计薪天数", self.rules_frame)
        lbl_days.setFixedWidth(85)
        lbl_days.setFont(QFont("Microsoft YaHei UI", 9, QFont.Weight.DemiBold))
        self.edit_workdays_per_month = QLineEdit(self.rules_frame)
        self.edit_workdays_per_month.setPlaceholderText("默认 26")

        lbl_payday = QLabel("次月发薪日", self.rules_frame)
        lbl_payday.setFixedWidth(72)
        lbl_payday.setFont(QFont("Microsoft YaHei UI", 9, QFont.Weight.DemiBold))
        self.combo_payday = QComboBox(self.rules_frame)
        for d in range(1, 32):
            self.combo_payday.addItem(f"次月 {d} 日", d)

        row_days.addWidget(lbl_days)
        row_days.addWidget(self.edit_workdays_per_month)
        row_days.addSpacing(12)
        row_days.addWidget(lbl_payday)
        row_days.addWidget(self.combo_payday)
        rules_layout.addLayout(row_days)

        # Weekdays Checkboxes
        row_weekdays = QHBoxLayout()
        lbl_wd = QLabel("工作日", self.rules_frame)
        lbl_wd.setFixedWidth(85)
        lbl_wd.setFont(QFont("Microsoft YaHei UI", 9, QFont.Weight.DemiBold))
        row_weekdays.addWidget(lbl_wd)

        self.weekday_checks: Dict[int, QCheckBox] = {}
        day_names = [(1, "一"), (2, "二"), (3, "三"), (4, "四"), (5, "五"), (6, "六"), (0, "日")]
        for day_idx, day_name in day_names:
            chk = QCheckBox(day_name, self.rules_frame)
            chk.setFont(QFont("Microsoft YaHei UI", 9, QFont.Weight.Medium))
            chk.setCursor(Qt.CursorShape.PointingHandCursor)
            self.weekday_checks[day_idx] = chk
            row_weekdays.addWidget(chk)
        rules_layout.addLayout(row_weekdays)

        # Adaptive Dock Mode Selector
        row_pos = QHBoxLayout()
        lbl_pos = QLabel("状态栏位置", self.rules_frame)
        lbl_pos.setFixedWidth(85)
        lbl_pos.setFont(QFont("Microsoft YaHei UI", 9, QFont.Weight.DemiBold))
        self.combo_dock_mode = QComboBox(self.rules_frame)
        self.combo_dock_mode.addItem("自动跟随托盘左侧 (推荐)", "tray_left")
        self.combo_dock_mode.addItem("自动跟随应用图标右侧", "apps_right")
        self.combo_dock_mode.addItem("状态栏居中对齐", "taskbar_center")
        self.combo_dock_mode.addItem("自由拖动磁吸位置", "custom")
        row_pos.addWidget(lbl_pos)
        row_pos.addWidget(self.combo_dock_mode)
        rules_layout.addLayout(row_pos)

        self.lbl_rules_hint = QLabel(
            "日薪 = 月薪 ÷ 计薪天数，只在工作日的上班时段累计。\n支持上下左右任务栏及托盘展开自动跟随，也可直接拖动胶囊。",
            self.rules_frame
        )
        self.lbl_rules_hint.setFont(QFont("Microsoft YaHei UI", 8, QFont.Weight.Medium))
        rules_layout.addWidget(self.lbl_rules_hint)

        main_layout.addWidget(self.rules_frame)

        # Error Banner
        self.lbl_error = QLabel("", self.card)
        self.lbl_error.setFont(QFont("Microsoft YaHei UI", 9, QFont.Weight.DemiBold))
        self.lbl_error.setVisible(False)
        main_layout.addWidget(self.lbl_error)

        main_layout.addStretch()

        # Footer Buttons
        btn_layout = QHBoxLayout()
        self.hint_footer = QLabel("点击状态栏胶囊可随时查看实时详情", self.card)
        self.hint_footer.setFont(QFont("Microsoft YaHei UI", 8, QFont.Weight.Medium))
        btn_layout.addWidget(self.hint_footer)
        btn_layout.addStretch()

        self.btn_cancel = QPushButton("取消", self.card)
        self.btn_cancel.setFont(QFont("Microsoft YaHei UI", 9, QFont.Weight.DemiBold))
        self.btn_cancel.setCursor(Qt.CursorShape.PointingHandCursor)
        self.btn_cancel.clicked.connect(self.reject)

        self.btn_save = QPushButton("保存修改", self.card)
        self.btn_save.setFont(QFont("Microsoft YaHei UI", 9, QFont.Weight.Bold))
        self.btn_save.setObjectName("SaveButton")
        self.btn_save.setCursor(Qt.CursorShape.PointingHandCursor)
        self.btn_save.setDefault(True)
        self.btn_save.clicked.connect(self.on_save)

        btn_layout.addWidget(self.btn_cancel)
        btn_layout.addWidget(self.btn_save)
        main_layout.addLayout(btn_layout)

    def load_settings(self, settings: IncomeSettings, dock_mode: str = "tray_left"):
        idx = self.combo_currency.findData(settings.currency)
        if idx >= 0:
            self.combo_currency.setCurrentIndex(idx)
        self.edit_salary.setText(f"{settings.monthly_salary:.2f}")
        self.time_start.setTime(QTime(settings.work_start // 60, settings.work_start % 60))
        self.time_end.setTime(QTime(settings.work_end // 60, settings.work_end % 60))
        self.chk_lunch.setChecked(settings.has_lunch_break)
        self.time_lunch_start.setTime(QTime(settings.lunch_start // 60, settings.lunch_start % 60))
        self.time_lunch_end.setTime(QTime(settings.lunch_end // 60, settings.lunch_end % 60))
        self.time_lunch_start.setEnabled(settings.has_lunch_break)
        self.time_lunch_end.setEnabled(settings.has_lunch_break)
        self.edit_workdays_per_month.setText(str(settings.monthly_workdays))
        pay_idx = self.combo_payday.findData(settings.payday)
        if pay_idx >= 0:
            self.combo_payday.setCurrentIndex(pay_idx)
        for day_idx, chk in self.weekday_checks.items():
            chk.setChecked(day_idx in settings.workdays)
        pos_idx = self.combo_dock_mode.findData(dock_mode)
        if pos_idx >= 0:
            self.combo_dock_mode.setCurrentIndex(pos_idx)

    def update_styles(self):
        is_dark = is_apps_dark_mode()
        fg = "#ECFDF5" if is_dark else "#064E3B"
        fg_sec = "#A7F3D0" if is_dark else "#047857"
        amber_accent = "#FBBF24" if is_dark else "#F59E0B"
        input_bg = "rgba(19, 56, 45, 0.85)" if is_dark else "#FFFFFF"
        border = "rgba(52, 211, 153, 0.45)" if is_dark else "#A7F3D0"
        btn_bg = "rgba(19, 56, 45, 0.85)" if is_dark else "#F0FDF4"
        btn_hover = "rgba(52, 211, 153, 0.25)" if is_dark else "#D1FAE5"
        save_bg = "#10B981"
        save_fg = "#FFFFFF"
        save_border = "#34D399" if is_dark else "#6EE7B7"
        save_hover = "#059669"
        save_hover_fg = "#FFFFFF"

        self.card.setStyleSheet(f"""
            QLabel {{
                color: {fg};
                background: transparent;
            }}
            QLineEdit, QComboBox, QTimeEdit {{
                background-color: {input_bg};
                color: {fg};
                border: 1.8px solid {border};
                border-radius: 10px;
                padding: 5px 10px;
                font-size: 12px;
            }}
            QLineEdit:focus, QComboBox:focus, QTimeEdit:focus {{
                border: 2px solid {amber_accent};
            }}
            QComboBox QAbstractItemView {{
                background-color: {"#0F2E24" if is_dark else "#FFFFFF"};
                color: {fg};
                selection-background-color: {save_bg};
                selection-color: {save_fg};
                border: 1.8px solid {border};
                border-radius: 10px;
            }}
            QPushButton {{
                background-color: {btn_bg};
                color: {fg};
                border: 1.8px solid {border};
                border-radius: 10px;
                padding: 6px 18px;
                font-weight: 600;
            }}
            QPushButton:hover {{
                background-color: {btn_hover};
                border: 1.8px solid {save_border};
            }}
            QPushButton#CloseButton {{
                padding: 0px;
                border-radius: 15px;
                font-size: 13px;
            }}
            QPushButton#SaveButton {{
                background-color: {save_bg};
                color: {save_fg};
                border: 2px solid {save_border};
                border-radius: 10px;
                font-weight: 700;
            }}
            QPushButton#SaveButton:hover {{
                background-color: {save_hover};
                color: {save_hover_fg};
            }}
            QCheckBox {{
                color: {fg};
                spacing: 4px;
                background: transparent;
            }}
        """)
        self.kicker_label.setStyleSheet(f"color: {amber_accent}; background: transparent;")
        self.subtitle_label.setStyleSheet(f"color: {fg_sec}; background: transparent;")
        self.lbl_overnight_hint.setStyleSheet(f"color: {fg_sec}; background: transparent;")
        self.lbl_rules_hint.setStyleSheet(f"color: {fg_sec}; background: transparent;")
        self.hint_footer.setStyleSheet(f"color: {fg_sec}; background: transparent;")
        self.lbl_error.setStyleSheet("color: #DC2626; background: transparent;")

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

    def on_save(self):
        try:
            curr = self.combo_currency.currentData()
            sal_str = self.edit_salary.text().strip().replace(",", "")
            if not sal_str:
                raise SettingsError("请填写月薪金额。")
            salary = float(sal_str)

            start_t = self.time_start.time()
            work_start = start_t.hour() * 60 + start_t.minute()

            end_t = self.time_end.time()
            work_end = end_t.hour() * 60 + end_t.minute()

            has_lunch = self.chk_lunch.isChecked()
            ls_t = self.time_lunch_start.time()
            lunch_start = ls_t.hour() * 60 + ls_t.minute()
            le_t = self.time_lunch_end.time()
            lunch_end = le_t.hour() * 60 + le_t.minute()

            days_str = self.edit_workdays_per_month.text().strip()
            if not days_str:
                raise SettingsError("请填写每月计薪天数。")
            workdays_per_month = float(days_str)

            payday_val = int(self.combo_payday.currentData() or 15)
            selected_workdays = [d for d, chk in self.weekday_checks.items() if chk.isChecked()]

            settings = IncomeSettings(
                currency=curr,
                monthly_salary=salary,
                work_start=work_start,
                work_end=work_end,
                has_lunch_break=has_lunch,
                lunch_start=lunch_start,
                lunch_end=lunch_end,
                monthly_workdays=workdays_per_month,
                workdays=selected_workdays,
                payday=payday_val
            )
            validated = settings.validated()
            selected_dock_mode = self.combo_dock_mode.currentData()
            self.lbl_error.setVisible(False)
            self.saved.emit(validated)
            if selected_dock_mode:
                self.dock_mode_changed.emit(selected_dock_mode)
            self.accept()
        except SettingsError as e:
            self.lbl_error.setText(e.message)
            self.lbl_error.setVisible(True)
        except ValueError:
            self.lbl_error.setText("请填写有效的数字。")
            self.lbl_error.setVisible(True)
        except Exception as e:
            self.lbl_error.setText(str(e))
            self.lbl_error.setVisible(True)
