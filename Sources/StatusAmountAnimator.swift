import AppKit

/// Template frames retain the menu bar's native foreground and highlight handling.
enum StatusAmountRenderer {
    static let font = NSFont.monospacedDigitSystemFont(ofSize: 11, weight: .medium)
    static func width(for text: String) -> CGFloat {
        max(62, ceil((text as NSString).size(withAttributes: [.font: font]).width) + 22)
    }
    static func image(from old: String, to new: String, progress: Double, width: CGFloat) -> NSImage {
        let t = min(1, max(0, progress))
        let eased = t * t * (3 - 2 * t)
        let count = max(old.count, new.count)
        let previous = Array(String(repeating: " ", count: count - old.count) + old)
        let next = Array(String(repeating: " ", count: count - new.count) + new)
        let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: NSColor.black]
        let widths = (0..<count).map { index in
            max(String(previous[index]).size(withAttributes: attributes).width, String(next[index]).size(withAttributes: attributes).width)
        }
        let image = NSImage(size: NSSize(width: width, height: 22), flipped: false) { rect in
            let border = NSBezierPath(roundedRect: NSRect(x: 1, y: 2.5, width: rect.width - 2, height: 17), xRadius: 8.5, yRadius: 8.5)
            NSColor.black.withAlphaComponent(0.38).setStroke()
            border.lineWidth = 0.8
            border.stroke()
            NSGraphicsContext.saveGraphicsState()
            NSBezierPath(rect: NSRect(x: 6, y: 3, width: rect.width - 12, height: 16)).addClip()
            var x = (rect.width - widths.reduce(0, +)) / 2
            let textHeight = "0".size(withAttributes: attributes).height
            let y = (rect.height - textHeight) / 2
            for index in 0..<count {
                let a = String(previous[index])
                let b = String(next[index])
                func draw(_ glyph: String, at baseline: CGFloat, opacity: CGFloat) {
                    var style = attributes
                    style[.foregroundColor] = NSColor.black.withAlphaComponent(opacity)
                    let glyphWidth = glyph.size(withAttributes: style).width
                    glyph.draw(at: NSPoint(x: x + (widths[index] - glyphWidth) / 2, y: baseline), withAttributes: style)
                }
                if a == b {
                    draw(b, at: y, opacity: 1)
                } else {
                    // Each changed digit slides up inside the ring; unchanged digits stay still.
                    if t < 1 { draw(a, at: y + 15 * eased, opacity: 1 - eased) }
                    if t > 0 { draw(b, at: y - 15 * (1 - eased), opacity: eased) }
                }
                x += widths[index]
            }
            NSGraphicsContext.restoreGraphicsState()
            return true
        }
        image.isTemplate = true
        return image
    }
}

/// Render only during a transition. The latest queued amount wins if updates arrive mid-roll.
final class StatusAmountAnimator {
    private weak var button: NSStatusBarButton?
    private var timer: Timer?
    private var current = ""
    private var pending: (text: String, width: CGFloat)?
    private var startTime: TimeInterval = 0
    private var transitionFrom = ""
    private var transitionTo = ""
    private var transitionWidth: CGFloat = 62
    private let duration: TimeInterval = 0.82
    init(button: NSStatusBarButton) { self.button = button }
    deinit { timer?.invalidate() }

    func show(_ text: String, width: CGFloat, animated: Bool) {
        if !animated || current.isEmpty {
            timer?.invalidate()
            timer = nil
            pending = nil
            current = text
            button?.image = StatusAmountRenderer.image(from: text, to: text, progress: 1, width: width)
            return
        }
        if timer != nil {
            pending = (text, width)
            return
        }
        guard text != current else { return }
        transitionFrom = current
        transitionTo = text
        transitionWidth = width
        startTime = ProcessInfo.processInfo.systemUptime
        let timer = Timer(timeInterval: 1.0 / 30, repeats: true) { [weak self] _ in self?.tick() }
        self.timer = timer
        RunLoop.main.add(timer, forMode: .common)
        tick()
    }
    private func tick() {
        let elapsed = ProcessInfo.processInfo.systemUptime - startTime
        let progress = min(1, elapsed / duration)
        button?.image = StatusAmountRenderer.image(from: transitionFrom, to: transitionTo, progress: progress, width: transitionWidth)
        if progress >= 1 {
            timer?.invalidate()
            timer = nil
            current = transitionTo
            if let next = pending {
                pending = nil
                show(next.text, width: next.width, animated: !NSWorkspace.shared.accessibilityDisplayShouldReduceMotion)
            }
        }
    }
}
