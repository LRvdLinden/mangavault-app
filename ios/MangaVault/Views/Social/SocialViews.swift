import SwiftUI

// Social screens: notification bell, friends, messages / chat, a friend's collection
// and the Manga Room. Ported from the web app (mangas.base44.app); all data goes
// through the `Social` wrappers (social* backend functions) and Base44 entities.

// MARK: - Shared helpers

fileprivate func svIsCancellation(_ error: Error) -> Bool {
    if error is CancellationError { return true }
    if let u = error as? URLError, u.code == .cancelled { return true }
    return false
}

/// Avatar: the image when an avatar URL exists, else the first letter of the name in a circle.
fileprivate struct SVAvatar: View {
    let url: String?
    let name: String
    var size: CGFloat = 44

    private var initial: String {
        let t = name.trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? "?" : String(t.prefix(1)).uppercased()
    }

    var body: some View {
        ZStack {
            if let u = nonEmpty(url) {
                RemoteImage(url: u)
            } else {
                Circle().fill(Theme.primary.opacity(0.2))
                Text(initial)
                    .font(.system(size: size * 0.42, weight: .black))
                    .foregroundStyle(Theme.primary)
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .overlay(Circle().stroke(Theme.border, lineWidth: 1))
    }
}

/// Small rounded action button used in social rows.
fileprivate struct SVSmallButton: View {
    let title: String
    var icon: String? = nil
    var filled = false
    var color: Color = Theme.primary
    var disabled = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                if let icon { Image(systemName: icon) }
                Text(title)
            }
            .font(.caption.weight(.bold))
            .padding(.horizontal, 10).padding(.vertical, 7)
            .foregroundStyle(filled ? Color.white : color)
            .background(filled ? color : Theme.card, in: RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(filled ? Color.clear : Theme.border))
        }
        .buttonStyle(.plain)
        .disabled(disabled)
        .opacity(disabled ? 0.5 : 1)
    }
}

/// A user row: avatar, display name (links to the public profile), bio and trailing actions.
fileprivate struct SVUserRow<Actions: View>: View {
    let user: SocialUser
    @ViewBuilder var actions: Actions

    var body: some View {
        HStack(spacing: 12) {
            NavigationLink(value: Route.publicProfile(user.id)) {
                SVAvatar(url: user.avatar_url, name: user.name, size: 46)
            }
            .buttonStyle(.plain)
            VStack(alignment: .leading, spacing: 3) {
                NavigationLink(value: Route.publicProfile(user.id)) {
                    Text(user.name)
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(Theme.foreground)
                        .lineLimit(1)
                }
                .buttonStyle(.plain)
                if let bio = nonEmpty(user.bio) {
                    Text(bio).font(.caption).foregroundStyle(Theme.muted).lineLimit(2)
                }
            }
            Spacer(minLength: 6)
            HStack(spacing: 6) { actions }
        }
        .padding(12)
        .cardStyle()
    }
}

// MARK: - Notification bell

struct NotificationBell: View {
    @Environment(AppState.self) private var app
    @State private var showing = false

    private var badgeText: String {
        app.unreadNotifications > 9 ? "9+" : "\(app.unreadNotifications)"
    }

    var body: some View {
        Button { showing = true } label: {
            ZStack(alignment: .topTrailing) {
                Image(systemName: "bell")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Theme.foreground)
                    .frame(width: 34, height: 34)
                if app.unreadNotifications > 0 {
                    Text(badgeText)
                        .font(.system(size: 9, weight: .black))
                        .foregroundStyle(Color.white)
                        .padding(.horizontal, 4)
                        .frame(minWidth: 16, minHeight: 16)
                        .background(Theme.red, in: Capsule())
                }
            }
        }
        .accessibilityLabel("Notifications")
        .sheet(isPresented: $showing) {
            SVNotificationsSheet()
                .environment(app)
                .presentationDetents([.medium, .large])
        }
    }
}

fileprivate struct SVNotificationsSheet: View {
    @Environment(AppState.self) private var app
    @Environment(\.dismiss) private var dismiss
    @State private var marking = false

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 10) {
                    if app.notifications.isEmpty {
                        EmptyState(icon: "bell.slash", text: "No notifications yet.")
                    } else {
                        ForEach(app.notifications) { n in
                            row(n)
                        }
                    }
                }
                .padding(16)
            }
            .background(Theme.background)
            .navigationTitle("Notifications")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if app.unreadNotifications > 0 {
                        Button {
                            Task { await markAll() }
                        } label: {
                            if marking { ProgressView() } else { Text("Mark all read") }
                        }
                        .disabled(marking)
                    }
                }
            }
            .task { await app.refreshSocialCounts() }
        }
        .tint(Theme.primary)
    }

    private func row(_ n: VaultNotification) -> some View {
        let unread = n.read != true
        return Button { openNotification(n) } label: {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: icon(for: n.type))
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(Theme.primary)
                    .frame(width: 34, height: 34)
                    .background(Theme.primary.opacity(0.15), in: Circle())
                VStack(alignment: .leading, spacing: 3) {
                    Text(n.title ?? "Notification")
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(Theme.foreground)
                        .multilineTextAlignment(.leading)
                    if let bodyText = nonEmpty(n.body) {
                        Text(bodyText)
                            .font(.caption)
                            .foregroundStyle(Theme.muted)
                            .multilineTextAlignment(.leading)
                            .lineLimit(3)
                    }
                    Text(ServerDate.relative(n.created_date))
                        .font(.caption2)
                        .foregroundStyle(Theme.muted.opacity(0.8))
                }
                Spacer(minLength: 4)
                if unread {
                    Circle().fill(Theme.primary).frame(width: 8, height: 8).padding(.top, 6)
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(unread ? Theme.primary.opacity(0.06) : Color.clear, in: RoundedRectangle(cornerRadius: 14))
            .cardStyle(highlighted: unread)
        }
        .buttonStyle(.plain)
    }

    private func icon(for type: String?) -> String {
        switch type ?? "" {
        case "message": return "bubble.left.fill"
        case "friend_request": return "person.badge.plus"
        case "friend_accepted": return "person.2.fill"
        default: return "bell.fill"
        }
    }

    private func openNotification(_ n: VaultNotification) {
        let type = n.type ?? ""
        var route: Route? = nil
        if type == "message" { route = .messages }
        else if type == "friend_request" || type == "friend_accepted" { route = .friends }
        dismiss()
        guard let route else { return }
        let state = app
        Task { @MainActor in
            // Let the sheet finish dismissing before navigating.
            try? await Task.sleep(for: .milliseconds(350))
            state.openRoute = route
        }
    }

    private func markAll() async {
        marking = true
        await Social.markAllNotificationsRead()
        await app.refreshSocialCounts()
        marking = false
    }
}

// MARK: - Friends

fileprivate enum SVFriendsTab: String, CaseIterable {
    case friends, requests, sent, find
}

struct FriendsView: View {
    @Environment(AppState.self) private var app
    @State private var tab: SVFriendsTab = .friends
    @State private var overview = FriendsOverview()
    @State private var loading = true
    @State private var busyId: String?
    @State private var removing: SocialUser?

    // Find
    @State private var query = ""
    @State private var results: [SocialUser] = []
    @State private var searching = false
    @State private var sentIds: Set<String> = []

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(lead: "MY", accent: "FRIENDS", subtitle: "Find readers, manage requests and chat.")
                tabPicker
                content
            }
            .padding(16)
        }
        .background(Theme.background)
        .navigationTitle("Friends")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await loadOverview() }
        .task {
            while !Task.isCancelled {
                await loadOverview()
                try? await Task.sleep(for: .seconds(20))
            }
        }
        .task(id: query) {
            try? await Task.sleep(for: .milliseconds(300))
            guard !Task.isCancelled else { return }
            await runSearch()
        }
        .confirmationDialog("Remove friend?",
                            isPresented: Binding(get: { removing != nil }, set: { if !$0 { removing = nil } }),
                            titleVisibility: .visible,
                            presenting: removing) { user in
            Button("Remove", role: .destructive) {
                Task { await remove(user) }
            }
            Button("Cancel", role: .cancel) { removing = nil }
        } message: { user in
            Text("\(user.name) will no longer be your friend and will lose access to your private collection.")
        }
    }

    private func label(_ t: SVFriendsTab) -> String {
        switch t {
        case .friends: return "Friends (\(overview.friends.count))"
        case .requests: return "Requests (\(overview.incoming.count))"
        case .sent: return "Sent (\(overview.outgoing.count))"
        case .find: return "Find"
        }
    }

    private var tabPicker: some View {
        Picker("Section", selection: $tab) {
            ForEach(SVFriendsTab.allCases, id: \.self) { t in
                Text(label(t)).tag(t)
            }
        }
        .pickerStyle(.segmented)
    }

    @ViewBuilder private var content: some View {
        switch tab {
        case .friends: friendsList
        case .requests: incomingList
        case .sent: outgoingList
        case .find: findSection
        }
    }

    @ViewBuilder private var friendsList: some View {
        if loading && overview.friends.isEmpty {
            LoadingView()
        } else if overview.friends.isEmpty {
            EmptyState(icon: "person.2", text: "No friends yet. Use Find to search for users.")
        } else {
            LazyVStack(spacing: 10) {
                ForEach(overview.friends) { user in
                    SVUserRow(user: user) {
                        if let uid = user.user_id {
                            NavigationLink(value: Route.chat(userId: uid)) {
                                Image(systemName: "bubble.left.fill")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundStyle(Color.white)
                                    .frame(width: 34, height: 34)
                                    .background(Theme.primary, in: RoundedRectangle(cornerRadius: 8))
                            }
                            .buttonStyle(.plain)
                        }
                        Button { removing = user } label: {
                            Image(systemName: "person.badge.minus")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundStyle(Theme.red)
                                .frame(width: 34, height: 34)
                                .background(Theme.card, in: RoundedRectangle(cornerRadius: 8))
                                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Theme.border))
                        }
                        .buttonStyle(.plain)
                        .disabled(busyId == user.id)
                    }
                }
            }
        }
    }

    @ViewBuilder private var incomingList: some View {
        if loading && overview.incoming.isEmpty {
            LoadingView()
        } else if overview.incoming.isEmpty {
            EmptyState(icon: "tray", text: "No pending requests.")
        } else {
            LazyVStack(spacing: 10) {
                ForEach(overview.incoming) { user in
                    SVUserRow(user: user) {
                        SVSmallButton(title: "Accept", icon: "checkmark", filled: true, disabled: busyId == user.id) {
                            guard let rid = user.request_id else { return }
                            Task { await act(user.id, "accept", ["request_id": rid], toast: "Friend request accepted") }
                        }
                        SVSmallButton(title: "Decline", color: Theme.muted, disabled: busyId == user.id) {
                            guard let rid = user.request_id else { return }
                            Task { await act(user.id, "decline", ["request_id": rid], toast: "Request declined") }
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder private var outgoingList: some View {
        if loading && overview.outgoing.isEmpty {
            LoadingView()
        } else if overview.outgoing.isEmpty {
            EmptyState(icon: "paperplane", text: "No sent requests.")
        } else {
            LazyVStack(spacing: 10) {
                ForEach(overview.outgoing) { user in
                    SVUserRow(user: user) {
                        SVSmallButton(title: "Cancel", icon: "xmark", color: Theme.muted, disabled: busyId == user.id) {
                            guard let rid = user.request_id else { return }
                            Task { await act(user.id, "cancel", ["request_id": rid], toast: "Request cancelled") }
                        }
                    }
                }
            }
        }
    }

    private var findSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").foregroundStyle(Theme.muted)
                TextField("Search by username...", text: $query)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                if searching { ProgressView().controlSize(.small) }
            }
            .padding(12)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border))

            if query.trimmingCharacters(in: .whitespaces).isEmpty {
                EmptyState(icon: "person.crop.circle.badge.questionmark", text: "Search for readers by their username.")
            } else if results.isEmpty && !searching {
                EmptyState(icon: "person.slash", text: "No users found.")
            } else {
                LazyVStack(spacing: 10) {
                    ForEach(results) { user in
                        SVUserRow(user: user) { findAction(user) }
                    }
                }
            }
        }
    }

    @ViewBuilder private func findAction(_ user: SocialUser) -> some View {
        if user.friend == true {
            Badge(text: "FRIENDS", icon: "checkmark", fg: Theme.emerald, bg: Theme.emerald.opacity(0.18), ring: Theme.emerald.opacity(0.4))
        } else if let uid = user.user_id, sentIds.contains(uid) {
            Badge(text: "REQUESTED", icon: "clock", fg: Theme.muted, bg: Theme.card, ring: Theme.border)
        } else {
            SVSmallButton(title: "Add", icon: "person.badge.plus", filled: true, disabled: busyId == user.id || user.user_id == nil) {
                guard let uid = user.user_id else { return }
                Task {
                    let ok = await act(user.id, "send", ["target_user_id": uid], toast: "Friend request sent")
                    if ok { sentIds.insert(uid) }
                    await runSearch()
                }
            }
        }
    }

    // MARK: Data

    private func loadOverview() async {
        do {
            let o = try await Social.overview()
            overview = o
        } catch {
            if !svIsCancellation(error) && loading { app.fail("Could not load friends", error) }
        }
        loading = false
    }

    private func runSearch() async {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { results = []; searching = false; return }
        searching = true
        do {
            let r = try await Social.search(q)
            // Ignore stale responses when the query changed meanwhile.
            if q == query.trimmingCharacters(in: .whitespaces) { results = r }
        } catch {
            if !svIsCancellation(error) { results = [] }
        }
        searching = false
    }

    @discardableResult
    private func act(_ rowId: String, _ action: String, _ params: [String: Any], toast: String?) async -> Bool {
        busyId = rowId
        var ok = false
        do {
            try await Social.friends(action, params)
            if let toast { app.show(toast) }
            ok = true
        } catch {
            app.fail("Action failed", error)
        }
        busyId = nil
        await loadOverview()
        await app.refreshSocialCounts()
        return ok
    }

    private func remove(_ user: SocialUser) async {
        removing = nil
        guard let uid = user.user_id else { return }
        await act(user.id, "remove", ["target_user_id": uid], toast: "Friend removed")
    }
}

// MARK: - Messages

struct MessagesView: View {
    @Environment(AppState.self) private var app
    @State private var conversations: [Conversation] = []
    @State private var loading = true

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(lead: "MY", accent: "MESSAGES", subtitle: "Chat with your manga friends.")
                content
            }
            .padding(16)
        }
        .background(Theme.background)
        .navigationTitle("Messages")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await load() }
        .task {
            while !Task.isCancelled {
                await load()
                try? await Task.sleep(for: .seconds(15))
            }
        }
    }

    @ViewBuilder private var content: some View {
        if loading && conversations.isEmpty {
            LoadingView()
        } else if conversations.isEmpty {
            EmptyState(icon: "bubble.left.and.bubble.right",
                       text: "No conversations yet. Accept a friend request or message a friend from their profile.")
        } else {
            LazyVStack(spacing: 10) {
                ForEach(conversations) { c in
                    if let uid = c.other_user_id {
                        NavigationLink(value: Route.chat(userId: uid)) { row(c) }
                            .buttonStyle(.plain)
                    } else {
                        row(c)
                    }
                }
            }
        }
    }

    private func row(_ c: Conversation) -> some View {
        let name = nonEmpty(c.other?.display_name) ?? "Reader"
        let unread = c.unread ?? 0
        return HStack(spacing: 12) {
            SVAvatar(url: c.other?.avatar_url, name: name, size: 48)
                .overlay(alignment: .topTrailing) {
                    if unread > 0 {
                        Text(unread > 9 ? "9+" : "\(unread)")
                            .font(.system(size: 9, weight: .black))
                            .foregroundStyle(Color.white)
                            .padding(.horizontal, 4)
                            .frame(minWidth: 18, minHeight: 18)
                            .background(Theme.primary, in: Capsule())
                            .overlay(Capsule().stroke(Theme.background, lineWidth: 2))
                            .offset(x: 4, y: -4)
                    }
                }
            VStack(alignment: .leading, spacing: 3) {
                HStack {
                    Text(name)
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(Theme.foreground)
                        .lineLimit(1)
                    Spacer(minLength: 4)
                    Text(ServerDate.relative(c.last_message_date))
                        .font(.caption2)
                        .foregroundStyle(Theme.muted)
                }
                Text(nonEmpty(c.last_message) ?? "No messages yet")
                    .font(.caption)
                    .foregroundStyle(unread > 0 ? Theme.foreground : Theme.muted)
                    .fontWeight(unread > 0 ? .semibold : .regular)
                    .lineLimit(1)
            }
            Image(systemName: "chevron.right").font(.caption.weight(.bold)).foregroundStyle(Theme.muted)
        }
        .padding(12)
        .contentShape(Rectangle())
        .cardStyle(highlighted: unread > 0)
    }

    private func load() async {
        do {
            conversations = try await Social.conversations()
        } catch {
            if !svIsCancellation(error) && loading { app.fail("Could not load messages", error) }
        }
        loading = false
    }
}

// MARK: - Chat

/// Opens the chat with a user. Finds the existing conversation; if there is none yet the
/// composer still works — the first message creates the conversation on the server.
struct ChatLauncherView: View {
    let otherUserId: String

    var body: some View {
        SVChatScreen(otherUserId: otherUserId)
    }
}

fileprivate struct SVChatScreen: View {
    let otherUserId: String
    @Environment(AppState.self) private var app

    @State private var conversation: Conversation?
    @State private var messages: [ChatMessage] = []
    @State private var loaded = false
    @State private var text = ""
    @State private var sending = false
    @State private var markedOnce = false

    private var myId: String? { app.user?.id }
    private var trimmed: String { text.trimmingCharacters(in: .whitespacesAndNewlines) }

    var body: some View {
        VStack(spacing: 0) {
            header
            messageList
        }
        .background(Theme.background)
        .navigationTitle(conversation?.other?.name ?? "Chat")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            while !Task.isCancelled {
                await refresh()
                try? await Task.sleep(for: .seconds(4))
            }
        }
    }

    @ViewBuilder private var header: some View {
        if let other = conversation?.other {
            NavigationLink(value: Route.publicProfile(other.id)) {
                HStack(spacing: 10) {
                    SVAvatar(url: other.avatar_url, name: other.name, size: 36)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(other.name).font(.subheadline.weight(.bold)).foregroundStyle(Theme.foreground)
                        Text("View profile").font(.caption2).foregroundStyle(Theme.muted)
                    }
                    Spacer()
                    Image(systemName: "chevron.right").font(.caption.weight(.bold)).foregroundStyle(Theme.muted)
                }
                .padding(.horizontal, 16).padding(.vertical, 10)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .background(Theme.card)
            .overlay(alignment: .bottom) { Rectangle().fill(Theme.border).frame(height: 1) }
        }
    }

    private var messageList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 8) {
                    if !loaded {
                        LoadingView()
                    } else if messages.isEmpty {
                        EmptyState(icon: "hand.wave", text: "Say hello!")
                    } else {
                        ForEach(messages) { m in
                            SVBubble(message: m, mine: m.sender_id != nil && m.sender_id == myId)
                                .id(m.id)
                        }
                    }
                }
                .padding(16)
            }
            .scrollDismissesKeyboard(.interactively)
            .safeAreaInset(edge: .bottom) { composer }
            .onChange(of: messages.count, initial: true) { _, _ in
                guard let last = messages.last?.id else { return }
                withAnimation(.easeOut(duration: 0.2)) { proxy.scrollTo(last, anchor: .bottom) }
            }
        }
    }

    private var composer: some View {
        HStack(alignment: .bottom, spacing: 10) {
            TextField("Type a message...", text: $text, axis: .vertical)
                .lineLimit(1...4)
                .padding(.horizontal, 12).padding(.vertical, 10)
                .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
                .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
                .onChange(of: text) { _, v in
                    if v.count > 1000 { text = String(v.prefix(1000)) }
                }
            Button { Task { await send() } } label: {
                Group {
                    if sending { ProgressView().tint(.white) }
                    else { Image(systemName: "paperplane.fill").font(.system(size: 16, weight: .bold)) }
                }
                .foregroundStyle(Color.white)
                .frame(width: 42, height: 42)
                .background(trimmed.isEmpty ? Theme.secondary : Theme.primary, in: Circle())
            }
            .buttonStyle(.plain)
            .disabled(trimmed.isEmpty || sending)
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .background(Theme.background.opacity(0.97))
        .overlay(alignment: .top) { Rectangle().fill(Theme.border).frame(height: 1) }
    }

    // MARK: Data

    private func refresh() async {
        do {
            if conversation == nil {
                let all = try await Social.conversations()
                conversation = all.first { $0.other_user_id == otherUserId }
            }
            guard let c = conversation else {
                loaded = true
                return
            }
            let fetched = try await Social.messages(c.id)
            let sorted = fetched.sorted { ($0.created_date ?? "") < ($1.created_date ?? "") }
            if sorted != messages { messages = sorted }
            loaded = true
            let me = myId
            let hasUnread = sorted.contains { $0.read != true && $0.sender_id != me }
            if hasUnread || !markedOnce {
                markedOnce = true
                await Social.markRead(c.id)
                await app.refreshSocialCounts()
            }
        } catch {
            if !svIsCancellation(error) { loaded = true }
        }
    }

    private func send() async {
        let content = String(trimmed.prefix(1000))
        guard !content.isEmpty, !sending else { return }
        sending = true
        do {
            try await Social.send(to: otherUserId, content)
            text = ""
            sending = false
            await refresh()
        } catch {
            sending = false
            app.fail("Failed to send", error)
        }
    }
}

fileprivate struct SVBubble: View {
    let message: ChatMessage
    let mine: Bool

    var body: some View {
        HStack {
            if mine { Spacer(minLength: 48) }
            VStack(alignment: mine ? .trailing : .leading, spacing: 3) {
                Text(message.content ?? "")
                    .font(.subheadline)
                    .foregroundStyle(mine ? Color.white : Theme.foreground)
                    .multilineTextAlignment(.leading)
                Text(ServerDate.short(message.created_date))
                    .font(.system(size: 9))
                    .foregroundStyle(mine ? Color.white.opacity(0.75) : Theme.muted)
            }
            .padding(.horizontal, 12).padding(.vertical, 8)
            .background(mine ? Theme.primary : Theme.secondary, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            if !mine { Spacer(minLength: 48) }
        }
    }
}

// MARK: - A friend's collection

struct UserCollectionView: View {
    let profileId: String
    @Environment(AppState.self) private var app
    @Environment(\.dismiss) private var dismiss

    @State private var data: SocialCollectionResponse?
    @State private var loading = true
    @State private var search = ""
    @State private var statusFilter: ReadStatus?
    @State private var sort = "recent"
    @State private var sending = false
    @State private var requestSent = false

    private static let columns = [GridItem(.adaptive(minimum: 110, maximum: 170), spacing: 12)]

    private var ownerName: String { data?.owner?.name ?? "Reader" }
    private var items: [CollectionItem] { data?.items ?? [] }

    private var filtered: [CollectionItem] {
        let q = search.trimmingCharacters(in: .whitespaces).lowercased()
        var r = items.filter { q.isEmpty || $0.titleText.lowercased().contains(q) }
        if let statusFilter { r = r.filter { $0.effectiveStatus == statusFilter } }
        switch sort {
        case "title":
            r.sort { $0.titleText.localizedCompare($1.titleText) == .orderedAscending }
        case "volume":
            r.sort { a, b in
                let va = a.volume_number ?? 0
                let vb = b.volume_number ?? 0
                if va != vb { return va < vb }
                return a.titleText.localizedCompare(b.titleText) == .orderedAscending
            }
        default:
            r.sort { ($0.created_date ?? "") > ($1.created_date ?? "") }
        }
        return r
    }

    var body: some View {
        ScrollView {
            if loading {
                LoadingView()
            } else if let data {
                if data.authorized == false {
                    lockedView(data)
                } else {
                    collectionView(data)
                }
            } else {
                EmptyState(icon: "books.vertical", text: "Collection not found.")
            }
        }
        .background(Theme.background)
        .navigationTitle(data?.authorized == false ? "Private Collection" : "\(ownerName)'s Collection")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await load() }
        .task { await load() }
    }

    private func lockedView(_ d: SocialCollectionResponse) -> some View {
        VStack(spacing: 16) {
            Image(systemName: "lock.fill")
                .font(.system(size: 34, weight: .bold))
                .foregroundStyle(Theme.primary)
                .frame(width: 76, height: 76)
                .background(Theme.primary.opacity(0.15), in: Circle())
            Text("Private Collection").font(.title2.weight(.black))
            Text("\(ownerName)'s collection is private. You need to be friends to view it.")
                .font(.subheadline).foregroundStyle(Theme.muted).multilineTextAlignment(.center)
            if d.friend == true || requestSent {
                Label("Your friend request is pending.", systemImage: "clock")
                    .font(.subheadline.weight(.bold)).foregroundStyle(Theme.muted)
            } else {
                Button { Task { await sendRequest() } } label: {
                    Group {
                        if sending { ProgressView().tint(.white) }
                        else { Label("Send Friend Request", systemImage: "person.badge.plus") }
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(sending || d.owner?.user_id == nil)
            }
            Button("Back to profile") { dismiss() }
                .font(.subheadline.weight(.bold))
                .foregroundStyle(Theme.primary)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .cardStyle()
        .padding(16)
    }

    private func collectionView(_ d: SocialCollectionResponse) -> some View {
        VStack(alignment: .leading, spacing: 18) {
            collectionHeader(d)
            controls
            grid
        }
        .padding(16)
    }

    private func collectionHeader(_ d: SocialCollectionResponse) -> some View {
        HStack(spacing: 12) {
            SVAvatar(url: d.owner?.avatar_url, name: ownerName, size: 52)
            VStack(alignment: .leading, spacing: 4) {
                Text("\(ownerName)'s Collection").font(.title3.weight(.black)).lineLimit(2)
                Text("\(items.count) volume\(items.count == 1 ? "" : "s")")
                    .font(.subheadline).foregroundStyle(Theme.muted)
                if d.friend == true && d.collection_public != true {
                    Badge(text: "PRIVATE COLLECTION — VISIBLE TO FRIENDS", icon: "lock.fill",
                          fg: Theme.primary, bg: Theme.primary.opacity(0.15), ring: Theme.primary.opacity(0.4))
                } else if d.collection_public == true {
                    Badge(text: "PUBLIC", icon: "globe", fg: Theme.emerald, bg: Theme.emerald.opacity(0.18), ring: Theme.emerald.opacity(0.4))
                }
            }
            Spacer(minLength: 0)
        }
    }

    private var controls: some View {
        VStack(spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").foregroundStyle(Theme.muted)
                TextField("Search titles...", text: $search)
                    .autocorrectionDisabled()
            }
            .padding(12)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border))

            HStack(spacing: 10) {
                Menu {
                    Button { statusFilter = nil } label: {
                        if statusFilter == nil { Label("All statuses", systemImage: "checkmark") } else { Text("All statuses") }
                    }
                    ForEach(ReadStatus.volumeCases) { s in
                        Button { statusFilter = s } label: {
                            if statusFilter == s { Label(s.label, systemImage: "checkmark") } else { Text(s.label) }
                        }
                    }
                } label: {
                    menuLabel(statusFilter?.label ?? "All statuses", icon: "line.3.horizontal.decrease.circle")
                }
                Menu {
                    Picker("Sort", selection: $sort) {
                        Text("Recently added").tag("recent")
                        Text("Title A-Z").tag("title")
                        Text("Volume number").tag("volume")
                    }
                } label: {
                    menuLabel(sortLabel, icon: "arrow.up.arrow.down")
                }
            }
        }
    }

    private var sortLabel: String {
        switch sort {
        case "title": return "Title A-Z"
        case "volume": return "Volume number"
        default: return "Recently added"
        }
    }

    private func menuLabel(_ text: String, icon: String) -> some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
            Text(text).lineLimit(1)
            Spacer(minLength: 0)
            Image(systemName: "chevron.down").font(.caption2)
        }
        .font(.caption.weight(.bold))
        .foregroundStyle(Theme.foreground)
        .padding(.horizontal, 12).padding(.vertical, 10)
        .frame(maxWidth: .infinity)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border))
    }

    @ViewBuilder private var grid: some View {
        if items.isEmpty {
            EmptyState(icon: "books.vertical", text: "This collection is empty for now.")
        } else if filtered.isEmpty {
            EmptyState(icon: "magnifyingglass", text: "No volumes match your filters.")
        } else {
            LazyVGrid(columns: Self.columns, spacing: 18) {
                ForEach(filtered) { item in
                    card(item)
                }
            }
        }
    }

    private func route(_ i: CollectionItem) -> Route {
        if let v = i.volume, !i.isOmnibus { return .volume(mangaId: i.manga_id ?? "", volume: v) }
        return .manga(i.manga_id ?? "")
    }

    private func card(_ item: CollectionItem) -> some View {
        let st: ReadStatus? = item.status.flatMap { ReadStatus(rawValue: $0) }
        return VStack(alignment: .leading, spacing: 4) {
            NavigationLink(value: route(item)) {
                CoverView(info: CoverInfo(item: item))
                    .overlay(alignment: .topTrailing) {
                        if let st, st != .plan_to_read {
                            Badge(text: st.label.uppercased(), bg: Theme.statusColor(st)).padding(6)
                        }
                    }
                    .overlay(alignment: .topLeading) {
                        if item.read == true {
                            Badge(text: "READ", icon: "checkmark", bg: Theme.emerald).padding(6)
                        }
                    }
                    .shadow(color: .black.opacity(0.35), radius: 6, y: 3)
            }
            .buttonStyle(.plain)
            .disabled(item.manga_id == nil)
            Text(item.titleText).font(.caption.weight(.bold)).foregroundStyle(Theme.foreground).lineLimit(1)
            Text(item.volumeLabel).font(.caption2).foregroundStyle(Theme.muted).lineLimit(1)
        }
    }

    // MARK: Data

    private func load() async {
        do {
            data = try await Social.collection(profileId)
        } catch {
            if !svIsCancellation(error) { app.fail("Could not load collection", error) }
        }
        loading = false
    }

    private func sendRequest() async {
        guard let uid = data?.owner?.user_id else { return }
        sending = true
        do {
            try await Social.friends("send", ["target_user_id": uid])
            requestSent = true
            app.show("Friend request sent")
            await app.refreshSocialCounts()
        } catch {
            app.fail("Action failed", error)
        }
        sending = false
    }
}

// MARK: - Manga Room

fileprivate struct SVRoomProfileName: Decodable {
    let id: String
    let display_name: String?
}

fileprivate struct SVDragOrigin {
    let index: Int
    let x: Double
    let y: Double
}

fileprivate func svClamp(_ v: Double, _ lo: Double, _ hi: Double) -> Double {
    min(max(v, lo), hi)
}

struct MangaRoomView: View {
    /// nil (or the signed-in user's id) = my own editable room; otherwise a read-only room.
    let userId: String?
    @Environment(AppState.self) private var app

    @State private var items: [RoomItem] = []
    @State private var layout: RoomLayout?
    @State private var isPublic = false
    @State private var ownerName = ""
    @State private var favorites: [RoomItem] = []
    @State private var loading = true
    @State private var saving = false
    @State private var togglingPublic = false
    @State private var confirmReset = false
    @State private var dragOrigin: SVDragOrigin?

    private var myId: String? { app.user?.id }
    private var isOwn: Bool { userId == nil || userId == myId }
    private var targetId: String? { userId ?? myId }

    private var headerSubtitle: String {
        if isOwn { return "Arrange your collection, cards, and decorations." }
        let name: String = nonEmpty(ownerName) ?? "Reader"
        return "\(name)'s Manga Room"
    }

    private var shareURL: URL? {
        guard let myId else { return nil }
        return Config.appBaseURL.appendingPathComponent("u/\(myId)/room")
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(lead: "MANGA", accent: "ROOM", subtitle: headerSubtitle)
                if loading {
                    LoadingView()
                } else {
                    if isOwn { actionBar }
                    if !isOwn && layout == nil {
                        EmptyState(icon: "sofa", text: "This room is empty or private.")
                    } else {
                        roomCanvas
                    }
                    if isOwn { palette }
                }
            }
            .padding(16)
        }
        .scrollDisabled(dragOrigin != nil)
        .background(Theme.background)
        .navigationTitle("Manga Room")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .confirmationDialog("Remove all objects from your room?", isPresented: $confirmReset, titleVisibility: .visible) {
            Button("Reset", role: .destructive) { items = [] }
            Button("Cancel", role: .cancel) {}
        }
    }

    // MARK: Actions

    private var actionBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                SVSmallButton(title: isPublic ? "Public" : "Private",
                              icon: isPublic ? "globe" : "lock.fill",
                              filled: isPublic,
                              color: isPublic ? Theme.emerald : Theme.muted,
                              disabled: togglingPublic) {
                    Task { await togglePublic() }
                }
                SVSmallButton(title: "Reset", icon: "arrow.counterclockwise", color: Theme.red, disabled: items.isEmpty) {
                    confirmReset = true
                }
                SVSmallButton(title: saving ? "Saving..." : "Save", icon: "square.and.arrow.down", filled: true, disabled: saving) {
                    Task { await save() }
                }
                if isPublic, let shareURL {
                    ShareLink(item: shareURL) {
                        HStack(spacing: 4) {
                            Image(systemName: "square.and.arrow.up")
                            Text("Share")
                        }
                        .font(.caption.weight(.bold))
                        .padding(.horizontal, 10).padding(.vertical, 7)
                        .foregroundStyle(Theme.primary)
                        .background(Theme.card, in: RoundedRectangle(cornerRadius: 8))
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Theme.border))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    // MARK: Canvas

    private var roomCanvas: some View {
        GeometryReader { geo in
            let size = geo.size
            ZStack(alignment: .topLeading) {
                SVRoomBackground()
                if items.isEmpty && isOwn {
                    Text("Add objects from the panel ↓")
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(Theme.muted)
                        .frame(width: size.width, height: size.height)
                }
                ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                    roomObject(index: index, item: item, size: size)
                }
            }
            .frame(width: size.width, height: size.height)
        }
        .aspectRatio(4.0 / 3.0, contentMode: .fit)
        .frame(minHeight: 360)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Theme.primary.opacity(0.25), lineWidth: 1))
    }

    private func roomObject(index: Int, item: RoomItem, size: CGSize) -> some View {
        let fx: CGFloat = CGFloat(svClamp(item.x, 2, 96)) / 100
        let fy: CGFloat = CGFloat(svClamp(item.y, 2, 95)) / 100
        let px: CGFloat = size.width * fx
        let py: CGFloat = size.height * fy
        let mask: GestureMask = isOwn ? .all : .subviews
        return SVRoomObject(item: item, editable: isOwn) {
            removeItem(at: index)
        }
        .scaleEffect(CGFloat(item.scale ?? 1))
        .highPriorityGesture(
            DragGesture(minimumDistance: 2, coordinateSpace: CoordinateSpace.global)
                .onChanged { v in drag(index: index, translation: v.translation, size: size) }
                .onEnded { _ in dragOrigin = nil },
            including: mask
        )
        .position(x: px, y: py)
    }

    private func drag(index: Int, translation: CGSize, size: CGSize) {
        guard isOwn, index < items.count, size.width > 0, size.height > 0 else { return }
        if dragOrigin?.index != index {
            dragOrigin = SVDragOrigin(index: index, x: items[index].x, y: items[index].y)
        }
        guard let o = dragOrigin else { return }
        let dxRatio: Double = Double(translation.width / size.width)
        let dyRatio: Double = Double(translation.height / size.height)
        let nx: Double = o.x + dxRatio * 100
        let ny: Double = o.y + dyRatio * 100
        items[index].x = svClamp(nx, 2, 96)
        items[index].y = svClamp(ny, 2, 95)
    }

    private func removeItem(at index: Int) {
        guard index < items.count else { return }
        dragOrigin = nil
        items.remove(at: index)
    }

    // MARK: Palette

    private var palette: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("ADD TO ROOM").font(.footnote.weight(.black)).tracking(1).foregroundStyle(Theme.muted)
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                paletteButton("Bookshelf", icon: "books.vertical.fill", kind: "shelf")
                paletteButton("Trophy", icon: "trophy.fill", kind: "trophy")
                paletteButton("Poster", icon: "photo", kind: "poster")
                paletteButton("Lamp", icon: "lamp.desk.fill", kind: "decoration")
            }
            Text("YOUR FAVORITES").font(.footnote.weight(.black)).tracking(1).foregroundStyle(Theme.muted)
            if favorites.isEmpty {
                Text("Mark volumes or cards as favorites to place them here.")
                    .font(.caption).foregroundStyle(Theme.muted)
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(Array(favorites.enumerated()), id: \.offset) { _, fav in
                            favoriteButton(fav)
                        }
                    }
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .cardStyle()
    }

    private func paletteButton(_ title: String, icon: String, kind: String) -> some View {
        Button {
            items.append(RoomItem(kind: kind, label: title, art: "", x: 50, y: 50, scale: 1))
        } label: {
            VStack(spacing: 6) {
                Image(systemName: icon).font(.system(size: 20)).foregroundStyle(Theme.primary)
                Text(title).font(.caption2.weight(.bold)).foregroundStyle(Theme.foreground).lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(Theme.secondary, in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border))
        }
        .buttonStyle(.plain)
    }

    private func favoriteButton(_ fav: RoomItem) -> some View {
        Button {
            var copy = fav
            copy.x = 50; copy.y = 50; copy.scale = 1
            items.append(copy)
        } label: {
            VStack(spacing: 4) {
                ZStack {
                    if let art = nonEmpty(fav.art) {
                        RemoteImage(url: art)
                    } else {
                        Rectangle().fill(Theme.secondary)
                        Image(systemName: fav.kind == "card" ? "rectangle.stack.fill" : "book.fill")
                            .foregroundStyle(Theme.muted)
                    }
                }
                .frame(width: 52, height: 72)
                .clipShape(RoundedRectangle(cornerRadius: 6))
                .overlay(alignment: .bottomTrailing) {
                    Image(systemName: "plus.circle.fill")
                        .font(.system(size: 14))
                        .symbolRenderingMode(.palette)
                        .foregroundStyle(Color.white, Theme.primary)
                        .padding(2)
                }
                Text(fav.label ?? "")
                    .font(.system(size: 9, weight: .bold))
                    .foregroundStyle(Theme.muted)
                    .lineLimit(1)
                    .frame(width: 60)
            }
        }
        .buttonStyle(.plain)
    }

    // MARK: Data

    private func load() async {
        guard let target = targetId else { loading = false; return }
        let api = Base44Client.shared
        do {
            let page = try await api.list("RoomLayout", RoomLayout.self, query: ["created_by_id": target], limit: 1)
            let found = page.items.first
            layout = found
            items = found?.items ?? []
            isPublic = found?.is_public ?? false
        } catch {
            if !svIsCancellation(error) { app.fail("Could not load room", error) }
        }
        if let p = try? await api.list("UserProfile", SVRoomProfileName.self, query: ["created_by_id": target],
                                       limit: 1, fields: ["id", "display_name"]),
           let name = nonEmpty(p.items.first?.display_name) {
            ownerName = name
        }
        if isOwn { await loadFavorites() }
        loading = false
    }

    private func loadFavorites() async {
        let api = Base44Client.shared
        var out: [RoomItem] = []
        if let vols = try? await api.list("CollectionItem", CollectionItem.self, query: ["favorite": true], limit: 24,
                                          fields: ["id", "title", "manga_id", "cover_image_url", "volume_cover_url"]) {
            for v in vols.items {
                let art = nonEmpty(v.volume_cover_url) ?? nonEmpty(v.cover_image_url) ?? ""
                out.append(RoomItem(kind: "volume", ref: v.id, label: v.title ?? "", art: art, x: 50, y: 50, scale: 1))
            }
        }
        if let favCards = try? await Engines.myCards(limit: 12, query: ["favorite": true]) {
            let ids = favCards.map(\.card_id).filter { !$0.isEmpty }
            if let cards = try? await Engines.cards(ids: ids) {
                for c in cards {
                    out.append(RoomItem(kind: "card", ref: c.card_id, label: c.title, art: c.artwork_url ?? "", x: 50, y: 50, scale: 1))
                }
            }
        }
        favorites = out
    }

    private func togglePublic() async {
        let newValue = !isPublic
        togglingPublic = true
        do {
            if let l = layout {
                try await Base44Client.shared.update("RoomLayout", l.id, ["is_public": newValue])
                layout?.is_public = newValue
            } else {
                let current: [[String: Any]] = items.map(\.json)
                let body: [String: Any] = ["items": current, "is_public": newValue]
                layout = try await Base44Client.shared.create("RoomLayout", body, RoomLayout.self)
            }
            isPublic = newValue
            app.show(newValue ? "Room is now public" : "Room is private")
        } catch {
            app.fail("Action failed", error)
        }
        togglingPublic = false
    }

    private func save() async {
        saving = true
        let payload: [[String: Any]] = items.map(\.json)
        do {
            if let l = layout {
                let body: [String: Any] = ["items": payload, "is_public": isPublic]
                try await Base44Client.shared.update("RoomLayout", l.id, body)
                layout?.items = items
                layout?.is_public = isPublic
            } else {
                let body: [String: Any] = ["items": payload, "is_public": isPublic]
                layout = try await Base44Client.shared.create("RoomLayout", body, RoomLayout.self)
            }
            app.show("Room saved!")
        } catch {
            app.fail("Save failed", error)
        }
        saving = false
    }
}

fileprivate struct SVRoomBackground: View {
    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            ZStack(alignment: .bottom) {
                LinearGradient(colors: [Color(hex: "#1a1410"), Color(hex: "#0d0d0d"), Color(hex: "#2a1a0a")],
                               startPoint: .top, endPoint: .bottom)
                Path { p in
                    var x: CGFloat = 60
                    while x < w {
                        p.move(to: CGPoint(x: x, y: 0))
                        p.addLine(to: CGPoint(x: x, y: h))
                        x += 60
                    }
                }
                .stroke(Theme.primary.opacity(0.06), lineWidth: 1)
                LinearGradient(colors: [Color(hex: "#3a2512"), Color(hex: "#1a0f06")], startPoint: .top, endPoint: .bottom)
                    .frame(height: h * 0.2)
                    .overlay(alignment: .top) { Rectangle().fill(Theme.primary.opacity(0.25)).frame(height: 1) }
            }
            .frame(width: w, height: h)
        }
    }
}

fileprivate struct SVRoomObject: View {
    let item: RoomItem
    let editable: Bool
    let onRemove: () -> Void

    private var iconName: String {
        switch item.kind {
        case "trophy": return "trophy.fill"
        case "shelf": return "books.vertical.fill"
        case "decoration": return "lamp.desk.fill"
        case "poster": return "photo"
        case "card": return "rectangle.stack.fill"
        default: return "book.fill"
        }
    }

    private var showsArt: Bool {
        guard nonEmpty(item.art) != nil else { return false }
        return item.kind == "volume" || item.kind == "card" || item.kind == "poster"
    }

    var body: some View {
        content
            .overlay(alignment: .topTrailing) {
                if editable {
                    Button(action: onRemove) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 18))
                            .symbolRenderingMode(.palette)
                            .foregroundStyle(Color.white, Theme.red)
                    }
                    .buttonStyle(.plain)
                    .offset(x: 8, y: -8)
                }
            }
    }

    @ViewBuilder private var content: some View {
        if showsArt {
            RemoteImage(url: item.art)
                .frame(width: 52, height: 72)
                .clipShape(RoundedRectangle(cornerRadius: 6))
                .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.white.opacity(0.15)))
                .shadow(color: .black.opacity(0.6), radius: 6, y: 4)
        } else {
            VStack(spacing: 3) {
                Image(systemName: iconName)
                    .font(.system(size: 26))
                    .foregroundStyle(Theme.primary)
                if let l = nonEmpty(item.label) {
                    Text(l)
                        .font(.system(size: 8, weight: .bold))
                        .foregroundStyle(Theme.muted)
                        .lineLimit(1)
                }
            }
            .frame(width: 62, height: 62)
            .background(Theme.card.opacity(0.85), in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.primary.opacity(0.3)))
            .shadow(color: .black.opacity(0.5), radius: 6, y: 4)
        }
    }
}
