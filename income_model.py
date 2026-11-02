from __future__ import annotations
import math
import json
import calendar
from dataclasses import dataclass, field
from datetime import datetime, date, timedelta, time
from typing import List, Dict, Optional, ClassVar
from enum import Enum

class Status(Enum):
    BEFORE = "before"
    WORKING = "working"
    LUNCH = "lunch"
    FINISHED = "finished"
    REST = "rest"

class SettingsError(Exception):
    def __init__(self, message: str):
        super().__init__(message)
        self.message = message

@dataclass
class IncomeSettings:
    currency: str = "CNY"
    monthly_salary: float = 5000.0
    work_start: int = 9 * 60          # 09:00 = 540
    work_end: int = 18 * 60           # 18:00 = 1080
    has_lunch_break: bool = True
    lunch_start: int = 12 * 60 + 30   # 12:30 = 750
    lunch_end: int = 14 * 60          # 14:00 = 840
    monthly_workdays: float = 26.0
    workdays: List[int] = field(default_factory=lambda: [1, 2, 3, 4, 5, 6]) # Mon-Sat (Sunday=0 off)
    payday: int = 15                  # 15th of next month

    CURRENCIES: ClassVar[List[str]] = ["CNY", "MYR", "TWD", "USD", "HKD", "EUR", "JPY", "GBP", "SGD"]
    CURRENCY_NAMES: ClassVar[Dict[str, str]] = {
        "CNY": "人民币", "MYR": "马币", "TWD": "新台币", "USD": "美元",
        "HKD": "港币", "EUR": "欧元", "JPY": "日元", "GBP": "英镑", "SGD": "新加坡元"
    }
    CURRENCY_SYMBOLS: ClassVar[Dict[str, str]] = {
        "CNY": "¥", "MYR": "RM ", "TWD": "NT$", "USD": "US$",
        "HKD": "HK$", "EUR": "€", "JPY": "JP¥", "GBP": "£", "SGD": "S$"
    }

    @property
    def daily_salary(self) -> float:
        return self.monthly_salary / self.monthly_workdays if self.monthly_workdays > 0 else 0.0

    @property
    def overnight(self) -> bool:
        return self.work_end < self.work_start

    @property
    def lunch_duration_minutes(self) -> int:
        if not self.has_lunch_break:
            return 0
        total_span = (self.work_end - self.work_start + 1440) % 1440
        l_offset_start = (self.lunch_start - self.work_start + 1440) % 1440
        l_offset_end = (self.lunch_end - self.work_start + 1440) % 1440
        if 0 < l_offset_start < l_offset_end < total_span:
            return l_offset_end - l_offset_start
        return 0

    @property
    def effective_work_minutes(self) -> int:
        total_span = (self.work_end - self.work_start + 1440) % 1440
        return max(1, total_span - self.lunch_duration_minutes)

    @property
    def hours_label(self) -> str:
        s = self.clock(self.work_start)
        e = self.clock(self.work_end)
        next_day = " 次日" if self.overnight else ""
        if self.has_lunch_break and self.lunch_duration_minutes > 0:
            ls = self.clock(self.lunch_start)
            le = self.clock(self.lunch_end)
            return f"{s}–{e}{next_day} (午休 {ls}–{le})"
        return f"{s}–{e}{next_day}"

    @property
    def lunch_label(self) -> str:
        if self.has_lunch_break and self.lunch_duration_minutes > 0:
            return f"{self.clock(self.lunch_start)}–{self.clock(self.lunch_end)}"
        return "无午休"

    def payday_label(self, now: Optional[datetime] = None) -> str:
        if now is None:
            now = datetime.now()
        today = now.date()
        p_day = max(1, min(31, self.payday))
        max_this = calendar.monthrange(today.year, today.month)[1]
        this_payday = date(today.year, today.month, min(p_day, max_this))
        if today == this_payday:
            return f"次月{p_day}日 · 今日发薪"
        elif today < this_payday:
            days_left = (this_payday - today).days
            return f"次月{p_day}日 · 距发薪 {days_left} 天"
        else:
            ny, nm = (today.year + 1, 1) if today.month == 12 else (today.year, today.month + 1)
            max_next = calendar.monthrange(ny, nm)[1]
            next_payday = date(ny, nm, min(p_day, max_next))
            days_left = (next_payday - today).days
            return f"次月{p_day}日 · 距发薪 {days_left} 天"

    @property
    def hourly_salary(self) -> float:
        eff_mins = self.effective_work_minutes
        if eff_mins <= 0:
            return 0.0
        return self.daily_salary / (eff_mins / 60.0)

    @staticmethod
    def clock(minutes: int) -> str:
        return f"{minutes // 60:02d}:{minutes % 60:02d}"

    def money(self, amount: float, digits: int = 2) -> str:
        sym = self.CURRENCY_SYMBOLS.get(self.currency, self.currency)
        return f"{sym}{amount:,.{digits}f}"

    def validated(self) -> IncomeSettings:
        if self.currency not in self.CURRENCIES:
            raise SettingsError("请选择有效币种。")
        if not math.isfinite(self.monthly_salary) or self.monthly_salary < 0 or self.monthly_salary > 10_000_000:
            raise SettingsError("月薪须介于 0 和 10,000,000 之间。")
        if not (0 <= self.work_start < 1440) or not (0 <= self.work_end < 1440) or self.work_start == self.work_end:
            raise SettingsError("上下班时间无效，且不能相同。")
        if self.has_lunch_break:
            total_span = (self.work_end - self.work_start + 1440) % 1440
            l_start = (self.lunch_start - self.work_start + 1440) % 1440
            l_end = (self.lunch_end - self.work_start + 1440) % 1440
            if not (0 < l_start < l_end < total_span):
                raise SettingsError("午休时间须处于上班与下班时间之间。")
        if not math.isfinite(self.monthly_workdays) or self.monthly_workdays <= 0 or self.monthly_workdays > 31:
            raise SettingsError("每月计薪天数须大于 0，且不超过 31。")
        if not self.workdays or not all(0 <= w <= 6 for w in self.workdays):
            raise SettingsError("请至少选择一个工作日。")
        if not (1 <= int(self.payday) <= 31):
            raise SettingsError("发薪日须介于 1 到 31 日之间。")
        sorted_workdays = sorted(list(set(self.workdays)))
        return IncomeSettings(
            currency=self.currency,
            monthly_salary=self.monthly_salary,
            work_start=self.work_start,
            work_end=self.work_end,
            has_lunch_break=self.has_lunch_break,
            lunch_start=self.lunch_start,
            lunch_end=self.lunch_end,
            monthly_workdays=self.monthly_workdays,
            workdays=sorted_workdays,
            payday=int(self.payday)
        )

    def to_dict(self) -> dict:
        return {
            "currency": self.currency,
            "monthly_salary": self.monthly_salary,
            "work_start": self.work_start,
            "work_end": self.work_end,
            "has_lunch_break": self.has_lunch_break,
            "lunch_start": self.lunch_start,
            "lunch_end": self.lunch_end,
            "monthly_workdays": self.monthly_workdays,
            "workdays": self.workdays,
            "payday": self.payday,
        }

    @classmethod
    def from_dict(cls, data: dict) -> IncomeSettings:
        return cls(
            currency=data.get("currency", "CNY"),
            monthly_salary=float(data.get("monthly_salary", 5000.0)),
            work_start=int(data.get("work_start", 540)),
            work_end=int(data.get("work_end", 1080)),
            has_lunch_break=bool(data.get("has_lunch_break", True)),
            lunch_start=int(data.get("lunch_start", 750)),
            lunch_end=int(data.get("lunch_end", 840)),
            monthly_workdays=float(data.get("monthly_workdays", 26.0)),
            workdays=data.get("workdays", [1, 2, 3, 4, 5, 6]),
            payday=int(data.get("payday", 15)),
        )

@dataclass
class WorkShift:
    start: datetime
    end: datetime
    lunch_start: Optional[datetime] = None
    lunch_end: Optional[datetime] = None

@dataclass
class IncomeSnapshot:
    shift: WorkShift
    status: Status
    earned: float
    progress: float
    next_start: Optional[datetime]

    @property
    def status_label(self) -> str:
        if self.status == Status.BEFORE:
            return "尚未上班"
        elif self.status == Status.WORKING:
            return "正在赚钱"
        elif self.status == Status.LUNCH:
            return "午休时间"
        elif self.status == Status.FINISHED:
            return "今日已下班"
        elif self.status == Status.REST:
            return "今天休息"
        return ""

    def timing_label(self, now: datetime) -> str:
        if self.status == Status.LUNCH and self.shift.lunch_end is not None:
            return f"午休中 · 距下午开工 {self.duration((self.shift.lunch_end - now).total_seconds())}"
        if self.status == Status.WORKING:
            if self.shift.lunch_start is not None and now < self.shift.lunch_start:
                return f"距午休 {self.duration((self.shift.lunch_start - now).total_seconds())} · 距下班 {self.duration((self.shift.end - now).total_seconds())}"
            return f"距下班 {self.duration((self.shift.end - now).total_seconds())}"
        if self.next_start is not None:
            return f"距上班 {self.duration((self.next_start - now).total_seconds())}"
        return "按工作时间自动计薪"

    @staticmethod
    def duration(interval_seconds: float) -> str:
        minutes = max(0, int(math.ceil(interval_seconds / 60.0)))
        if minutes >= 1440:
            return f"{minutes // 1440}天 {(minutes % 1440) // 60}小时"
        if minutes >= 60:
            return f"{minutes // 60}小时 {minutes % 60}分钟"
        return f"{minutes}分钟"

class IncomeMath:
    @staticmethod
    def get_weekday_sun0(d: date | datetime) -> int:
        return (d.weekday() + 1) % 7

    @staticmethod
    def shift(on_day: date, settings: IncomeSettings, tz=None) -> WorkShift:
        start_dt = datetime(on_day.year, on_day.month, on_day.day,
                            settings.work_start // 60, settings.work_start % 60, 0, tzinfo=tz)
        end_day = on_day + timedelta(days=1) if settings.overnight else on_day
        end_dt = datetime(end_day.year, end_day.month, end_day.day,
                          settings.work_end // 60, settings.work_end % 60, 0, tzinfo=tz)

        l_start_dt = None
        l_end_dt = None
        if settings.has_lunch_break and settings.lunch_duration_minutes > 0:
            ls_day = on_day + timedelta(days=1) if settings.lunch_start < settings.work_start else on_day
            ls_cand = datetime(ls_day.year, ls_day.month, ls_day.day,
                               settings.lunch_start // 60, settings.lunch_start % 60, 0, tzinfo=tz)
            le_day = ls_day + timedelta(days=1) if settings.lunch_end < settings.lunch_start else ls_day
            le_cand = datetime(le_day.year, le_day.month, le_day.day,
                               settings.lunch_end // 60, settings.lunch_end % 60, 0, tzinfo=tz)
            if start_dt < ls_cand < le_cand < end_dt:
                l_start_dt = ls_cand
                l_end_dt = le_cand

        return WorkShift(start=start_dt, end=end_dt, lunch_start=l_start_dt, lunch_end=l_end_dt)

    @staticmethod
    def is_workday(on_day: date, settings: IncomeSettings) -> bool:
        return IncomeMath.get_weekday_sun0(on_day) in settings.workdays

    @classmethod
    def snapshot(cls, settings: IncomeSettings, now: datetime) -> IncomeSnapshot:
        day = now.date()
        tz = now.tzinfo
        cur_shift = cls.shift(day, settings, tz=tz)
        yesterday = day - timedelta(days=1)
        prev_shift = cls.shift(yesterday, settings, tz=tz)

        active_shift = cur_shift
        if settings.overnight and now < prev_shift.end and cls.is_workday(yesterday, settings):
            active_shift = prev_shift

        workday = cls.is_workday(active_shift.start.date(), settings)
        total_span = (active_shift.end - active_shift.start).total_seconds()
        lunch_span = 0.0
        if active_shift.lunch_start and active_shift.lunch_end:
            lunch_span = max(0.0, (active_shift.lunch_end - active_shift.lunch_start).total_seconds())
        effective_length = max(1.0, total_span - lunch_span)

        if not workday:
            status = Status.REST
            progress = 0.0
        elif now < active_shift.start:
            status = Status.BEFORE
            progress = 0.0
        elif now >= active_shift.end:
            status = Status.FINISHED
            progress = 1.0
        else:
            if active_shift.lunch_start and active_shift.lunch_end:
                if now < active_shift.lunch_start:
                    status = Status.WORKING
                    elapsed = (now - active_shift.start).total_seconds()
                elif now < active_shift.lunch_end:
                    status = Status.LUNCH
                    elapsed = (active_shift.lunch_start - active_shift.start).total_seconds()
                else:
                    status = Status.WORKING
                    elapsed = (
                        (active_shift.lunch_start - active_shift.start).total_seconds()
                        + (now - active_shift.lunch_end).total_seconds()
                    )
            else:
                status = Status.WORKING
                elapsed = (now - active_shift.start).total_seconds()
            progress = max(0.0, min(1.0, elapsed / effective_length))

        next_start = None
        for offset in range(9):
            cand_day = day + timedelta(days=offset)
            cand_shift = cls.shift(cand_day, settings, tz=tz)
            if cand_shift.start > now and cls.is_workday(cand_day, settings):
                next_start = cand_shift.start
                break

        earned = settings.daily_salary * progress
        return IncomeSnapshot(
            shift=active_shift,
            status=status,
            earned=earned,
            progress=progress,
            next_start=next_start
        )
