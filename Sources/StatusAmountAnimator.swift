import AppKit

/// Template frames retain the menu bar's native foreground and highlight handling.
enum StatusAmountRenderer {
    static let font = NSFont.monospacedDigitSystemFont(ofSize: 11, weight: .medium)
    static func width(for text: String) -> CGFloat {
        max(62, ceil((text as NSString).size(withAttributes: [.font: font]).width) + 22)
    }
    static func image(from old: String, to new: String, progress: Double, width: CGFloat, fastPhase: Double? = nil) -> NSImage {
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
                if index == count - 1, let phase = fastPhase {
                    let base = Int(floor(phase))
                    let fraction = phase - floor(phase)
                    // Continuous vertical motion; wrap from 9 to 0 without a hard glyph swap.
                    draw(String(base % 10), at: y + 15 * fraction, opacity: 1)
                    draw(String((base + 1) % 10), at: y - 15 * (1 - fraction), opacity: 1)
                } else if a == b {
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

/// Upper digits roll at the real earning cadence; the thousandths digit is a visual ticker.
final class StatusAmountAnimator {
    private weak var button: NSStatusBarButton?
    private var timer: Timer?
    private var current = ""
    private var transitionFrom = ""
    private var transitionTo = ""
    private var transitionWidth: CGFloat = 62
    private var startTime: TimeInterval = 0
    private var fast = false
    private let duration: TimeInterval = 0.8
    init(button: NSStatusBarButton) { self.button = button }
    deinit { timer?.invalidate() }

    func show(_ text: String, width: CGFloat, animated: Bool, earning: Bool = false) {
        let motion = animated && !NSWorkspace.shared.accessibilityDisplayShouldReduceMotion
        fast = earning && motion
        transitionWidth = width
        if current.isEmpty || !motion || !earning {
            current = text
            transitionFrom = text
            transitionTo = text
            startTime = ProcessInfo.processInfo.systemUptime - duration
        } else if text != transitionTo {
            // Once-per-second targets finish before the next target arrives.
            transitionFrom = current
            transitionTo = text
            startTime = ProcessInfo.processInfo.systemUptime
        }
        if fast || transitionFrom != transitionTo {
            if timer == nil {
                let timer = Timer(timeInterval: 1.0 / 60, repeats: true) { [weak self] _ in self?.tick() }
                self.timer = timer
                RunLoop.main.add(timer, forMode: .common)
            }
        } else {
            timer?.invalidate()
            timer = nil
        }
        tick()
    }
    private func tick() {
        let now = ProcessInfo.processInfo.systemUptime
        let progress = min(1, max(0, (now - startTime) / duration))
        let motion = !NSWorkspace.shared.accessibilityDisplayShouldReduceMotion
        // Twelve digit positions per second, rendered continuously at 60 fps.
        let phase: Double? = fast && motion ? now * 12 : nil
        button?.image = StatusAmountRenderer.image(from: transitionFrom, to: transitionTo, progress: motion ? progress : 1, width: transitionWidth, fastPhase: phase)
        if progress >= 1 || !motion {
            current = transitionTo
            transitionFrom = transitionTo
            if !fast || !motion {
                timer?.invalidate()
                timer = nil
            }
        }
    }
}
