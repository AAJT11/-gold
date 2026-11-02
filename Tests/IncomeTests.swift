import Foundation

@main
struct IncomeTests {
    static func main() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Shanghai")!
        func date(_ day: Int, _ hour: Int, _ minute: Int = 0) -> Date {
            calendar.date(from: DateComponents(year: 2026, month: 10, day: day, hour: hour, minute: minute))!
        }
        var settings = IncomeSettings()
        settings.monthlySalary = 21750
        func check(_ name: String, _ condition: @autoclosure () -> Bool) {
            precondition(condition(), name)
            print("PASS: \(name)")
        }
        func snapshot(_ day: Int, _ hour: Int, _ minute: Int = 0) -> IncomeSnapshot {
            IncomeMath.snapshot(settings, now: date(day, hour, minute), calendar: calendar)
        }
        check("before work earns zero", snapshot(26, 8, 59).earned == 0)
        check("work starts at zero", snapshot(26, 9).earned == 0 && snapshot(26, 9).status == .working)
        check("half shift earns half daily pay", abs(snapshot(26, 13, 30).earned - 500) < 1e-8)
        check("after work caps at daily pay", snapshot(26, 23).earned == 1000 && snapshot(26, 23).status == .finished)
        check("Saturday rests", snapshot(31, 13).earned == 0 && snapshot(31, 13).status == .rest)
        check("Friday after work finds Monday", calendar.component(.day, from: snapshot(30, 19).nextStart!) == 2)
        settings.workdays.append(6)
        check("selected Saturday earns", snapshot(31, 18).earned == 1000)
        settings.workdays = [1, 2, 3, 4, 5]
        settings.workStart = 22 * 60
        settings.workEnd = 6 * 60
        check("Friday shift continues on Saturday", snapshot(31, 2).earned == 500 && snapshot(31, 2).status == .working)
        check("overnight shift reaches full pay before Saturday rest", snapshot(31, 5, 59).earned > 990)
        check("Saturday midnight shift does not start", snapshot(31, 23).earned == 0)
        var ny = Calendar(identifier: .gregorian)
        ny.timeZone = TimeZone(identifier: "America/New_York")!
        settings.workdays = [0, 1, 2, 3, 4, 5, 6]
        settings.workStart = 0
        settings.workEnd = 8 * 60
        let springDay = ny.date(from: DateComponents(year: 2026, month: 3, day: 8, hour: 8))!
        let spring = IncomeMath.snapshot(settings, now: springDay, calendar: ny)
        check("full DST spring shift still earns one daily amount", spring.earned == 1000)
        let fallDay = ny.date(from: DateComponents(year: 2026, month: 11, day: 1, hour: 8))!
        check("full DST fall shift still earns one daily amount", IncomeMath.snapshot(settings, now: fallDay, calendar: ny).earned == 1000)
        for mutate in [
            { (s: inout IncomeSettings) in s.workStart = s.workEnd },
            { (s: inout IncomeSettings) in s.workdays = [] },
            { (s: inout IncomeSettings) in s.monthlySalary = -1 },
            { (s: inout IncomeSettings) in s.monthlyWorkdays = 0 },
            { (s: inout IncomeSettings) in s.currency = "INVALID" }
        ] {
            var bad = settings
            mutate(&bad)
            do { _ = try bad.validated(); fatalError("Invalid settings accepted") }
            catch { print("PASS: rejects invalid settings (\(error.localizedDescription))") }
        }
        let encoded = try JSONEncoder().encode(settings)
        let decoded = try JSONDecoder().decode(IncomeSettings.self, from: encoded)
        check("settings survive JSON round trip", decoded == settings)
        check("CNY formatted with yuan symbol", settings.money(123.45).contains("¥123.45"))
        check("countdown rounds remaining seconds up", IncomeSnapshot.duration(1) == "1分钟")
    }
}
