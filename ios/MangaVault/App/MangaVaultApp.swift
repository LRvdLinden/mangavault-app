import SwiftUI

@main
struct MangaVaultApp: App {
    @State private var app = AppState()

    init() {
        let nav = UINavigationBarAppearance()
        nav.configureWithOpaqueBackground()
        nav.backgroundColor = UIColor(Theme.background)
        nav.shadowColor = UIColor(Theme.border)
        nav.titleTextAttributes = [.foregroundColor: UIColor(Theme.foreground)]
        nav.largeTitleTextAttributes = [.foregroundColor: UIColor(Theme.foreground)]
        UINavigationBar.appearance().standardAppearance = nav
        UINavigationBar.appearance().scrollEdgeAppearance = nav
        UINavigationBar.appearance().compactAppearance = nav

        let tab = UITabBarAppearance()
        tab.configureWithOpaqueBackground()
        tab.backgroundColor = UIColor(Theme.background)
        tab.shadowColor = UIColor(Theme.border)
        UITabBar.appearance().standardAppearance = tab
        UITabBar.appearance().scrollEdgeAppearance = tab
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(app)
                .preferredColorScheme(.dark)
                .tint(Theme.primary)
                .onOpenURL { app.handle(url: $0) }
        }
    }
}

struct RootView: View {
    @Environment(AppState.self) private var app

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            switch app.phase {
            case .launching: SplashView()
            case .signedOut: AuthFlowView().transition(.opacity)
            case .signedIn: MainTabView().transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: app.phase)
        .overlay(alignment: .top) {
            if let t = app.toast {
                ToastView(toast: t)
                    .padding(.top, 6)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .onTapGesture { app.toast = nil }
            }
        }
        .animation(.spring(duration: 0.35), value: app.toast)
        .overlay { ConfettiView(trigger: app.confettiTrigger) }
        .task { await app.bootstrap() }
    }
}

struct SplashView: View {
    @State private var pulse = false
    var body: some View {
        VStack(spacing: 16) {
            LogoMark(size: 72)
                .scaleEffect(pulse ? 1.05 : 0.95)
                .animation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true), value: pulse)
            Wordmark(size: 28)
        }
        .onAppear { pulse = true }
    }
}

struct LogoMark: View {
    var size: CGFloat = 36
    var body: some View {
        RoundedRectangle(cornerRadius: size * 0.25, style: .continuous)
            .fill(Theme.primary)
            .frame(width: size, height: size)
            .overlay(Image(systemName: "flame.fill").font(.system(size: size * 0.5, weight: .bold)).foregroundStyle(.white))
            .shadow(color: Theme.primary.opacity(0.55), radius: size * 0.3)
    }
}

struct Wordmark: View {
    var size: CGFloat = 20
    private var wordmarkText: Text {
        let first: Text = Text("MANGA").foregroundStyle(Theme.foreground)
        let second: Text = Text("VAULT").foregroundStyle(Theme.primary)
        return first + second
    }
    var body: some View {
        wordmarkText
            .font(.system(size: size, weight: .black))
            .tracking(-1)
    }
}
