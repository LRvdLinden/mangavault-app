import SwiftUI

struct ToastView: View {
    let toast: Toast
    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: toast.isError ? "exclamationmark.triangle.fill" : "checkmark.circle.fill")
                .foregroundStyle(toast.isError ? Theme.red : Theme.primary)
            VStack(alignment: .leading, spacing: 2) {
                Text(toast.title).font(.subheadline.weight(.bold)).foregroundStyle(Theme.foreground)
                if let m = toast.message, !m.isEmpty {
                    Text(m).font(.caption).foregroundStyle(Theme.muted)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(toast.isError ? Theme.red.opacity(0.5) : Theme.border))
        .padding(.horizontal, 16)
        .shadow(color: .black.opacity(0.4), radius: 12, y: 6)
    }
}

/// Lightweight confetti burst (replaces canvas-confetti from the web app).
struct ConfettiView: View {
    let trigger: Int
    @State private var particles: [Particle] = []
    @State private var start = Date()

    struct Particle: Identifiable {
        let id = UUID()
        let color: Color
        let x: CGFloat
        let vx: CGFloat
        let vy: CGFloat
        let spin: Double
        let size: CGFloat
        let circle: Bool
    }

    private static let colors = ["#26ccff", "#a25afd", "#ff5e7e", "#88ff5a", "#fcff42", "#ffa62d", "#ff36ff", "#FF7A00"].map(Color.init(hex:))

    var body: some View {
        TimelineView(.animation(paused: particles.isEmpty)) { timeline in
            Canvas { ctx, size in
                let t: CGFloat = CGFloat(timeline.date.timeIntervalSince(start))
                let fade: CGFloat = 1 - t / 2.6
                let alpha: Double = Double(max(0, fade))
                let gravity: CGFloat = 450 * t * t
                for p in particles {
                    let px: CGFloat = size.width * p.x + p.vx * t
                    let baseY: CGFloat = size.height * 0.4
                    let py: CGFloat = baseY + p.vy * t + gravity
                    let degrees: Double = p.spin * Double(t) * 360
                    var c = ctx
                    c.opacity = alpha
                    c.translateBy(x: px, y: py)
                    c.rotate(by: .degrees(degrees))
                    let half: CGFloat = p.size / 2
                    let heightFactor: CGFloat = p.circle ? 1 : 0.6
                    let rect = CGRect(x: -half, y: -half, width: p.size, height: p.size * heightFactor)
                    let shape: Path = p.circle ? Path(ellipseIn: rect) : Path(rect)
                    c.fill(shape, with: .color(p.color))
                }
            }
        }
        .allowsHitTesting(false)
        .ignoresSafeArea()
        .onChange(of: trigger) { _, _ in burst() }
    }

    private func burst() {
        start = Date()
        particles = (0..<90).map { _ in
            let degrees: Double = Double.random(in: -125.0 ... -55.0)
            let angle: Double = degrees * Double.pi / 180
            let speed: CGFloat = CGFloat.random(in: 350...850)
            return Particle(color: Self.colors.randomElement()!,
                            x: 0.5,
                            vx: CGFloat(cos(angle)) * speed,
                            vy: CGFloat(sin(angle)) * speed,
                            spin: Double.random(in: -2...2),
                            size: CGFloat.random(in: 6...11),
                            circle: Bool.random())
        }
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(2.8))
            particles = []
        }
    }
}
