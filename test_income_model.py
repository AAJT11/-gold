from datetime import datetime, date, timezone, timedelta
from zoneinfo import ZoneInfo
import json
from income_model import IncomeSettings, IncomeMath, IncomeSnapshot, Status, SettingsError

def run_tests():
    shanghai_tz = ZoneInfo("Asia/Shanghai")
    def dt(day, hour, minute=0):
        return datetime(2026, 10, day, hour, minute, 0, tzinfo=shanghai_tz)

    settings = IncomeSettings()
    settings.monthly_salary = 26000.0
    settings.monthly_workdays = 26.0
    settings.workdays = [1, 2, 3, 4, 5, 6]
    settings.has_lunch_break = True
    settings.lunch_start = 12 * 60 + 30  # 12:30
    settings.lunch_end = 14 * 60         # 14:00
    settings.payday = 15

    def check(name, condition):
        assert condition, f"FAILED: {name}"
        print(f"PASS: {name}")

    def snap(day, hour, minute=0):
        return IncomeMath.snapshot(settings, dt(day, hour, minute))

    check("effective work minutes is 450 (7.5h)", settings.effective_work_minutes == 450)
    check("before work earns zero", snap(26, 8, 59).earned == 0)
    check("work starts at zero", snap(26, 9).earned == 0 and snap(26, 9).status == Status.WORKING)
    # 09:00 to 12:30 is 3.5 hours = 210 minutes out of 450 minutes
    check("lunch break pauses at 12:30 and 13:30", snap(26, 12, 30).status == Status.LUNCH and snap(26, 13, 30).status == Status.LUNCH)
    check("lunch break freezes earned amount", abs(snap(26, 12, 30).earned - snap(26, 13, 59).earned) < 1e-8)
    check("lunch break earned equals 210/450 of daily pay", abs(snap(26, 13, 0).earned - (1000.0 * 210 / 450)) < 1e-6)
    check("resumes working at 14:00", snap(26, 14, 0).status == Status.WORKING and abs(snap(26, 14, 0).earned - (1000.0 * 210 / 450)) < 1e-6)
    check("after work caps at daily pay", snap(26, 23).earned == 1000 and snap(26, 23).status == Status.FINISHED)
    check("Saturday is a workday", snap(31, 18).earned == 1000)
    # Oct 25, 2026 is a Sunday
    check("Sunday rests", snap(25, 13).earned == 0 and snap(25, 13).status == Status.REST)
    check("payday label includes 次月15日", "次月15日" in settings.payday_label(dt(26, 10)))

    # Test classic mode without lunch break
    settings.has_lunch_break = False
    settings.workdays = [1, 2, 3, 4, 5]
    check("half shift without lunch earns half daily pay", abs(snap(26, 13, 30).earned - 500) < 1e-8)
    check("Friday after work finds Monday", snap(30, 19).next_start.day == 2)

    settings.workdays.append(6)
    check("selected Saturday earns", snap(31, 18).earned == 1000)

    settings.workdays = [1, 2, 3, 4, 5]
    settings.work_start = 22 * 60
    settings.work_end = 6 * 60
    check("Friday shift continues on Saturday", snap(31, 2).earned == 500 and snap(31, 2).status == Status.WORKING)
    check("overnight shift reaches full pay before Saturday rest", snap(31, 5, 59).earned > 990)
    check("Saturday midnight shift does not start", snap(31, 23).earned == 0)

    ny_tz = ZoneInfo("America/New_York")
    settings.workdays = [0, 1, 2, 3, 4, 5, 6]
    settings.work_start = 0
    settings.work_end = 8 * 60
    spring_day = datetime(2026, 3, 8, 8, 0, 0, tzinfo=ny_tz)
    spring = IncomeMath.snapshot(settings, spring_day)
    check("full DST spring shift still earns one daily amount", spring.earned == 1000)

    fall_day = datetime(2026, 11, 1, 8, 0, 0, tzinfo=ny_tz)
    fall = IncomeMath.snapshot(settings, fall_day)
    check("full DST fall shift still earns one daily amount", fall.earned == 1000)

    # Validation checks
    bad_tests = [
        lambda s: setattr(s, "work_start", s.work_end),
        lambda s: setattr(s, "lunch_start", s.lunch_end),
        lambda s: setattr(s, "workdays", []),
        lambda s: setattr(s, "monthly_salary", -1.0),
        lambda s: setattr(s, "monthly_workdays", 0.0),
        lambda s: setattr(s, "payday", 32),
        lambda s: setattr(s, "currency", "INVALID")
    ]
    for fn in bad_tests:
        bad = IncomeSettings()
        fn(bad)
        try:
            bad.validated()
            assert False, "Should have thrown SettingsError"
        except SettingsError as e:
            print(f"PASS: rejects invalid settings ({e.message})")

    d = settings.to_dict()
    restored = IncomeSettings.from_dict(d)
    check("settings survive dict round trip", restored.to_dict() == settings.to_dict())
    check("CNY formatted with yuan symbol", "¥123.45" in settings.money(123.45))
    check("countdown rounds remaining seconds up", IncomeSnapshot.duration(1) == "1分钟")
    print("ALL TESTS PASSED!")

if __name__ == "__main__":
    run_tests()
