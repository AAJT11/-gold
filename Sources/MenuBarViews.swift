import SwiftUI

struct IncomeDetails: View {
    @ObservedObject var store: IncomeStore
    let edit: () -> Void
    let loginEnabled: () -> Bool
    let toggleLogin: () -> Void
    let quit: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var body: some View {
        let snapshot = store.snapshot
        let settings = store.settings
        VStack(alignment: .leading, spacing: 18) {
            HStack {
                Text("今日收入").font(.system(size: 13, weight: .semibold))
                Spacer()
                Text(snapshot.statusLabel).font(.system(size: 11)).foregroundStyle(.secondary)
            }
            Text(settings.money(snapshot.earned, digits: 3))
                .font(.system(size: 40, weight: .regular)).monospacedDigit().tracking(-1.4)
                .lineLimit(1).minimumScaleFactor(0.5)
                .frame(maxWidth: .infinity, minHeight: 50, alignment: .leading)
                .contentTransition(.numericText(value: snapshot.earned))
                .animation(reduceMotion ? nil : .easeInOut(duration: 0.3), value: (snapshot.earned * 1000).rounded())
                .accessibilityLabel("今日已赚 \(settings.money(snapshot.earned, digits: 3))")
            VStack(spacing: 9) {
                GeometryReader { geometry in
                    ZStack(alignment: .leading) {
                        Capsule().fill(.primary.opacity(0.07))
                        Capsule().fill(.primary.opacity(0.45)).frame(width: geometry.size.width * snapshot.progress)
                    }
                }.frame(height: 3)
                HStack {
                    Text(snapshot.timingLabel(now: store.now))
                    Spacer()
                    Text("\(Int((snapshot.progress * 100).rounded()))%")
                }.font(.system(size: 10)).foregroundStyle(.secondary)
            }
            Divider()
            VStack(spacing: 11) {
                detail("工作时间", settings.hoursLabel)
                if settings.lunchEnabled { detail("午休", settings.lunchLabel) }
                detail("月薪", settings.money(settings.monthlySalary))
                detail("日薪估算", settings.money(settings.dailySalary))
                detail("每小时", settings.money(snapshot.perHour))
            }
            Divider()
            HStack {
                Button("修改设置", action: edit).buttonStyle(.borderless)
                Spacer()
                Menu {
                    Button(loginEnabled() ? "关闭开机启动" : "开启开机启动", action: toggleLogin)
                    Divider()
                    Button("退出今日收入", action: quit)
                } label: {
                    Image(systemName: "ellipsis").frame(width: 22, height: 18)
                }.menuStyle(.borderlessButton).fixedSize().accessibilityLabel("更多选项")
            }.font(.system(size: 11))
        }
        .padding(22)
        .frame(width: 308)
        .tint(.primary)
        .accessibilityElement(children: .contain)
        .accessibilityLabel("今日收入详情")
    }
    private func detail(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label).foregroundStyle(.secondary)
            Spacer()
            Text(value).monospacedDigit()
        }.font(.system(size: 11))
    }
}
