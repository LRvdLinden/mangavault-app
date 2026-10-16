import SwiftUI
import AuthenticationServices

enum AuthScreen { case login, register, forgot }

struct AuthFlowView: View {
    @State private var screen: AuthScreen = .login

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                Spacer(minLength: 30)
                switch screen {
                case .login: LoginView(screen: $screen)
                case .register: RegisterView(screen: $screen)
                case .forgot: ForgotPasswordView(screen: $screen)
                }
            }
            .padding(20)
            .frame(maxWidth: 460)
            .frame(maxWidth: .infinity)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Theme.background.ignoresSafeArea())
        .animation(.easeInOut(duration: 0.2), value: screen)
    }
}

struct AuthCard<Content: View, Footer: View>: View {
    let icon: String
    let title: String
    let subtitle: String
    @ViewBuilder var content: Content
    @ViewBuilder var footer: Footer

    var body: some View {
        VStack(spacing: 22) {
            VStack(spacing: 10) {
                RoundedRectangle(cornerRadius: 14).fill(Theme.primary).frame(width: 56, height: 56)
                    .overlay(Image(systemName: icon).font(.title2.weight(.bold)).foregroundStyle(.white))
                    .shadow(color: Theme.primary.opacity(0.5), radius: 14)
                Text(title).font(.system(size: 28, weight: .black)).foregroundStyle(Theme.foreground)
                Text(subtitle).font(.subheadline).foregroundStyle(Theme.muted).multilineTextAlignment(.center)
            }
            VStack(spacing: 16) { content }
                .padding(20)
                .cardStyle()
            footer.font(.subheadline)
        }
    }
}

struct AuthField: View {
    let label: String
    let icon: String
    @Binding var text: String
    var secure = false
    var content: UITextContentType? = nil
    var keyboard: UIKeyboardType = .default
    var placeholder = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(.subheadline.weight(.semibold)).foregroundStyle(Theme.foreground)
            HStack(spacing: 10) {
                Image(systemName: icon).foregroundStyle(Theme.muted).frame(width: 18)
                Group {
                    if secure { SecureField(placeholder, text: $text) }
                    else { TextField(placeholder, text: $text) }
                }
                .textContentType(content)
                .keyboardType(keyboard)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            }
            .padding(.horizontal, 12).frame(height: 48)
            .background(Theme.background, in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border))
        }
    }
}

struct ErrorBanner: View {
    let text: String
    var body: some View {
        Text(text).font(.footnote).foregroundStyle(Theme.red)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12).background(Theme.red.opacity(0.1), in: RoundedRectangle(cornerRadius: 10))
    }
}

struct OrDivider: View {
    var body: some View {
        HStack {
            Rectangle().fill(Theme.border).frame(height: 1)
            Text("OR").font(.caption2.weight(.semibold)).foregroundStyle(Theme.muted)
            Rectangle().fill(Theme.border).frame(height: 1)
        }
    }
}

// MARK: - Google sign-in

@MainActor
final class GoogleSignIn: NSObject, ASWebAuthenticationPresentationContextProviding {
    private var session: ASWebAuthenticationSession?

    nonisolated func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        MainActor.assumeIsolated {
            let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            return scenes.flatMap { $0.windows }.first { $0.isKeyWindow } ?? ASPresentationAnchor()
        }
    }

    /// Opens the Base44 Google flow; the backend redirects to `mangavault://auth?access_token=…`.
    func signIn() async throws -> String {
        let callback = "\(Config.urlScheme)://auth"
        let url = Base44Client.shared.googleLoginURL(fromURL: callback)
        return try await withCheckedThrowingContinuation { cont in
            let s = ASWebAuthenticationSession(url: url, callbackURLScheme: Config.urlScheme) { url, error in
                if let error { cont.resume(throwing: error); return }
                let items = url.flatMap { URLComponents(url: $0, resolvingAgainstBaseURL: false)?.queryItems } ?? []
                if let token = items.first(where: { $0.name == "access_token" })?.value, !token.isEmpty {
                    cont.resume(returning: token)
                } else {
                    cont.resume(throwing: APIError(status: 0, message: "Google sign-in did not return a session."))
                }
            }
            s.presentationContextProvider = self
            s.prefersEphemeralWebBrowserSession = false
            self.session = s
            if !s.start() { cont.resume(throwing: APIError(status: 0, message: "Could not start Google sign-in.")) }
        }
    }
}

struct GoogleButton: View {
    @Environment(AppState.self) private var app
    @State private var google = GoogleSignIn()
    @State private var busy = false
    var onError: (String) -> Void

    var body: some View {
        Button {
            busy = true
            Task {
                do {
                    let token = try await google.signIn()
                    Base44Client.shared.token = token
                    await app.didSignIn()
                } catch let e as ASWebAuthenticationSessionError where e.code == .canceledLogin {
                    // user cancelled
                } catch {
                    onError(error.localizedDescription)
                }
                busy = false
            }
        } label: {
            HStack(spacing: 10) {
                if busy { ProgressView().tint(Theme.foreground) } else { GoogleG() }
                Text("Continue with Google")
            }
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(OutlineButtonStyle())
        .disabled(busy)
    }
}

struct GoogleG: View {
    var body: some View {
        Text("G").font(.system(size: 17, weight: .heavy, design: .rounded))
            .foregroundStyle(LinearGradient(colors: [Color(hex: "#4285F4"), Color(hex: "#EA4335"), Color(hex: "#FBBC05"), Color(hex: "#34A853")],
                                            startPoint: .topLeading, endPoint: .bottomTrailing))
    }
}

// MARK: - Login

struct LoginView: View {
    @Environment(AppState.self) private var app
    @Binding var screen: AuthScreen
    @State private var email = ""
    @State private var password = ""
    @State private var error = ""
    @State private var loading = false

    var body: some View {
        AuthCard(icon: "arrow.right.to.line", title: "Welcome back", subtitle: "Log in to your account") {
            GoogleButton { error = $0 }
            OrDivider()
            if !error.isEmpty { ErrorBanner(text: error) }
            AuthField(label: "Email", icon: "envelope", text: $email, content: .emailAddress, keyboard: .emailAddress, placeholder: "you@example.com")
            VStack(alignment: .trailing, spacing: 6) {
                AuthField(label: "Password", icon: "lock", text: $password, secure: true, content: .password, placeholder: "••••••••")
                Button("Forgot password?") { screen = .forgot }
                    .font(.caption.weight(.semibold)).foregroundStyle(Theme.primary)
            }
            Button {
                Task { await submit() }
            } label: {
                Group { if loading { ProgressView().tint(.white) } else { Text("Log in") } }.frame(maxWidth: .infinity)
            }
            .buttonStyle(PrimaryButtonStyle())
            .disabled(loading || email.isEmpty || password.isEmpty)
        } footer: {
            HStack(spacing: 4) {
                Text("Don't have an account?").foregroundStyle(Theme.muted)
                Button("Create one") { screen = .register }.foregroundStyle(Theme.primary).fontWeight(.semibold)
            }
        }
    }

    private func submit() async {
        error = ""; loading = true
        defer { loading = false }
        do {
            try await Base44Client.shared.login(email: email.trimmingCharacters(in: .whitespaces), password: password)
            await app.didSignIn()
        } catch {
            self.error = error.localizedDescription.isEmpty ? "Invalid email or password" : error.localizedDescription
        }
    }
}

// MARK: - Register + OTP

struct RegisterView: View {
    @Environment(AppState.self) private var app
    @Binding var screen: AuthScreen
    @State private var email = ""
    @State private var password = ""
    @State private var confirm = ""
    @State private var error = ""
    @State private var loading = false
    @State private var awaitingCode = false
    @State private var code = ""

    var body: some View {
        if awaitingCode {
            AuthCard(icon: "envelope", title: "Verify your email", subtitle: "We sent a code to \(email)") {
                if !error.isEmpty { ErrorBanner(text: error) }
                TextField("6-digit code", text: $code)
                    .keyboardType(.numberPad)
                    .textContentType(.oneTimeCode)
                    .font(.system(size: 28, weight: .bold, design: .monospaced))
                    .multilineTextAlignment(.center)
                    .frame(height: 56)
                    .background(Theme.background, in: RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border))
                    .onChange(of: code) { _, v in code = String(v.filter(\.isNumber).prefix(6)) }
                Button {
                    Task { await verify() }
                } label: {
                    Group { if loading { ProgressView().tint(.white) } else { Text("Verify") } }.frame(maxWidth: .infinity)
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(loading || code.count < 6)
            } footer: {
                HStack(spacing: 4) {
                    Text("Didn't receive the code?").foregroundStyle(Theme.muted)
                    Button("Resend") {
                        Task {
                            error = ""
                            do { try await Base44Client.shared.resendOtp(email: email); app.show("Code sent", "Check your email for the new code.") }
                            catch { self.error = error.localizedDescription }
                        }
                    }.foregroundStyle(Theme.primary).fontWeight(.semibold)
                }
            }
        } else {
            AuthCard(icon: "person.badge.plus", title: "Create your account", subtitle: "Sign up to get started") {
                GoogleButton { error = $0 }
                OrDivider()
                if !error.isEmpty { ErrorBanner(text: error) }
                AuthField(label: "Email", icon: "envelope", text: $email, content: .emailAddress, keyboard: .emailAddress, placeholder: "you@example.com")
                AuthField(label: "Password", icon: "lock", text: $password, secure: true, content: .newPassword, placeholder: "••••••••")
                AuthField(label: "Confirm Password", icon: "lock", text: $confirm, secure: true, content: .newPassword, placeholder: "••••••••")
                Button {
                    Task { await register() }
                } label: {
                    Group { if loading { ProgressView().tint(.white) } else { Text("Create account") } }.frame(maxWidth: .infinity)
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(loading || email.isEmpty || password.isEmpty)
            } footer: {
                HStack(spacing: 4) {
                    Text("Already have an account?").foregroundStyle(Theme.muted)
                    Button("Log in") { screen = .login }.foregroundStyle(Theme.primary).fontWeight(.semibold)
                }
            }
        }
    }

    private func register() async {
        error = ""
        guard password == confirm else { error = "Passwords do not match"; return }
        loading = true; defer { loading = false }
        do {
            try await Base44Client.shared.register(email: email.trimmingCharacters(in: .whitespaces), password: password)
            awaitingCode = true
        } catch { self.error = error.localizedDescription }
    }

    private func verify() async {
        error = ""; loading = true; defer { loading = false }
        do {
            try await Base44Client.shared.verifyOtp(email: email, code: code)
            if Base44Client.shared.token == nil {
                try await Base44Client.shared.login(email: email, password: password)
            }
            await app.didSignIn()
        } catch { self.error = error.localizedDescription.isEmpty ? "Invalid verification code" : error.localizedDescription }
    }
}

// MARK: - Forgot password

struct ForgotPasswordView: View {
    @Binding var screen: AuthScreen
    @State private var email = ""
    @State private var sent = false
    @State private var loading = false

    var body: some View {
        AuthCard(icon: "envelope", title: "Reset password", subtitle: "We'll send you a link to reset it") {
            if sent {
                Text("If an account exists with that email, you'll receive a password reset link shortly.")
                    .font(.subheadline).foregroundStyle(Theme.foreground).multilineTextAlignment(.center)
            } else {
                AuthField(label: "Email address", icon: "envelope", text: $email, content: .emailAddress, keyboard: .emailAddress, placeholder: "you@example.com")
                Button {
                    Task {
                        loading = true
                        try? await Base44Client.shared.resetPasswordRequest(email: email.trimmingCharacters(in: .whitespaces))
                        loading = false; sent = true
                    }
                } label: {
                    Group { if loading { ProgressView().tint(.white) } else { Text("Send reset link") } }.frame(maxWidth: .infinity)
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(loading || email.isEmpty)
            }
        } footer: {
            Button { screen = .login } label: { Label("Back to log in", systemImage: "arrow.left") }
                .foregroundStyle(Theme.primary).fontWeight(.semibold)
        }
    }
}
