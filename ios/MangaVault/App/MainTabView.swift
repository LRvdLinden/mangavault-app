import SwiftUI

enum Route: Hashable {
    case manga(String)
    case volume(mangaId: String, volume: Int)
    case discover
    case statistics
    case achievements
    case quests
    case wrapped
    case profile
    case publicProfile(String)
    case wishlist
    case calendar
    case recommendations
    case journal
    case random
    case compare
    case series
    case tierLists
    case friends
    case messages
    case chat(userId: String)
    case userCollection(String)
    // Build 2 — rewards, cards & events
    case rewards
    case capsules
    case cards
    case events
    case event(String)
    case christmasEvent
    case borutoEvent
    case roulette
    case room(userId: String?)
}

extension View {
    func vaultDestinations() -> some View {
        navigationDestination(for: Route.self) { route in
            switch route {
            case .manga(let id): MangaDetailView(mangaId: id)
            case .volume(let id, let vol): VolumeDetailView(mangaId: id, volume: vol)
            case .discover: CatalogView(mode: .discover)
            case .statistics: StatisticsView()
            case .achievements: AchievementsView()
            case .quests: QuestsView()
            case .wrapped: WrappedView()
            case .profile: ProfileView(profileId: nil)
            case .publicProfile(let id): ProfileView(profileId: id)
            case .wishlist: WishlistView()
            case .calendar: CalendarView()
            case .recommendations: RecommendationsView()
            case .journal: JournalView()
            case .random: RandomMangaView()
            case .compare: CompareView()
            case .series: SeriesTrackerView()
            case .tierLists: TierListsView()
            case .friends: FriendsView()
            case .messages: MessagesView()
            case .chat(let uid): ChatLauncherView(otherUserId: uid)
            case .userCollection(let id): UserCollectionView(profileId: id)
            case .rewards: RewardsView()
            case .capsules: CapsulesView()
            case .cards: CardsView()
            case .events: EventsView()
            case .event(let id): EventDetailView(eventId: id)
            case .christmasEvent: ChristmasEventView()
            case .borutoEvent: BorutoEventView()
            case .roulette: RouletteView()
            case .room(let uid): MangaRoomView(userId: uid)
            }
        }
    }

    /// Notification bell with unread badge, shown on the main screens.
    func vaultToolbar() -> some View {
        toolbar {
            ToolbarItem(placement: .topBarTrailing) { NotificationBell() }
        }
    }
}

/// Same tabs as the web app's mobile bottom bar (Home · Catalog · Collection · Events · More);
/// Wishlist, Friends and everything else live under More, like the web "More" page.
enum MainTab: Hashable { case home, catalog, collection, events, more }

struct MainTabView: View {
    @Environment(AppState.self) private var app
    @State private var tab: MainTab = .home
    @State private var morePath = NavigationPath()

    var body: some View {
        TabView(selection: $tab) {
            NavigationStack { DashboardView().vaultToolbar().vaultDestinations() }
                .tabItem { Label("Home", systemImage: "house.fill") }
                .tag(MainTab.home)

            NavigationStack { CatalogView(mode: .catalog).vaultDestinations() }
                .tabItem { Label("Catalog", systemImage: "book.fill") }
                .tag(MainTab.catalog)

            NavigationStack { CollectionView().vaultDestinations() }
                .tabItem { Label("Collection", systemImage: "books.vertical.fill") }
                .tag(MainTab.collection)

            NavigationStack { EventsView().vaultToolbar().vaultDestinations() }
                .tabItem { Label("Events", systemImage: "calendar.badge.clock") }
                .tag(MainTab.events)

            NavigationStack(path: $morePath) { MoreView().vaultToolbar().vaultDestinations() }
                .tabItem { Label("More", systemImage: "ellipsis.circle.fill") }
                .badge(app.unreadMessages + app.pendingFriendRequests)
                .tag(MainTab.more)
        }
        .onChange(of: app.pendingProfileId, initial: true) { _, id in
            guard let id else { return }
            tab = .more
            morePath.append(Route.publicProfile(id))
            app.pendingProfileId = nil
        }
        .onChange(of: app.requestedTab) { _, t in
            guard let t else { return }
            tab = t
            app.requestedTab = nil
        }
        .onChange(of: app.openRoute) { _, route in
            guard let route else { return }
            if route == .events { tab = .events } else {
                tab = .more
                morePath = NavigationPath()
                morePath.append(route)
            }
            app.openRoute = nil
        }
        .task {
            // Lightweight polling replaces the web app's realtime subscriptions.
            while !Task.isCancelled {
                await app.refreshSocialCounts()
                try? await Task.sleep(for: .seconds(30))
            }
        }
    }
}

/// Mirrors the web "More" page: every feature in one place.
struct MoreView: View {
    @Environment(AppState.self) private var app
    @State private var confirmLogout = false

    var body: some View {
        List {
            Section {
                NavigationLink(value: Route.profile) {
                    HStack(spacing: 12) {
                        LogoMark(size: 40)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(app.displayName).font(.headline)
                            Text(app.user?.email ?? "").font(.caption).foregroundStyle(Theme.muted)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
            Section("Featured") {
                row("Rewards", "gift.fill", .rewards, "Daily reward, level rewards and reward history.")
                row("Vault Capsules", "shippingbox.fill", .capsules, "Open free capsules earned by being active.")
                row("Trading Cards", "rectangle.stack.fill", .cards, "Collect and browse your trading cards.")
                row("Achievements", "trophy.fill", .achievements, "Badges and milestones you have earned.")
                row("Daily Quests", "target", .quests, "Daily and weekly reading challenges.")
                row("Statistics", "chart.bar.xaxis", .statistics, "Reading statistics and collection data.")
            }
            Section("Social") {
                NavigationLink(value: Route.friends) {
                    itemLabel("Friends", "person.2.fill", "Find readers and manage requests.")
                        .badge(app.pendingFriendRequests)
                }
                NavigationLink(value: Route.messages) {
                    itemLabel("Messages", "bubble.left.and.bubble.right.fill", "Chat with your manga friends.")
                        .badge(app.unreadMessages)
                }
                row("Manga Room", "sofa.fill", .room(userId: nil), "Decorate and share your own manga room.")
            }
            Section("Library") {
                row("Wishlist", "heart.fill", .wishlist, "Volumes you want to buy next.")
                row("Series Tracker", "books.vertical.fill", .series, "Follow progress per series.")
                row("Reading Calendar", "calendar", .calendar, "Your reading sessions by day.")
                row("Manga Journal", "note.text", .journal, "Reading notes and personal memories.")
                row("Tier Lists", "list.number", .tierLists, "Rank your favourite manga and characters.")
                row("Manga Wrapped", "sparkles", .wrapped, "Your personal manga year in review.")
            }
            Section("Discover") {
                row("Discover", "safari.fill", .discover, "Explore new series to start reading.")
                row("Recommendations", "wand.and.stars", .recommendations, "Picks based on your collection.")
                row("Manga Roulette", "circle.dotted.circle", .roulette, "Spin the wheel for your next read.")
                row("Random Manga", "dice.fill", .random, "Discover a random title from the catalog.")
                row("Manga Versus", "arrow.left.arrow.right", .compare, "Compare manga side by side.")
            }
            Section {
                Link(destination: Config.appBaseURL) {
                    Label("Open web version", systemImage: "globe")
                }
                Button(role: .destructive) { confirmLogout = true } label: {
                    Label("Log out", systemImage: "rectangle.portrait.and.arrow.right")
                }
            } footer: {
                Text("Manga Vault \(Bundle.main.appVersion) · data synced with mangas.base44.app")
            }
        }
        .scrollContentBackground(.hidden)
        .background(Theme.background)
        .navigationTitle("More")
        .confirmationDialog("Log out of Manga Vault?", isPresented: $confirmLogout, titleVisibility: .visible) {
            Button("Log out", role: .destructive) { app.signOut() }
        }
    }

    private func row(_ title: String, _ icon: String, _ route: Route, _ desc: String) -> some View {
        NavigationLink(value: route) { itemLabel(title, icon, desc) }
    }

    private func itemLabel(_ title: String, _ icon: String, _ desc: String) -> some View {
        Label {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                Text(desc).font(.caption).foregroundStyle(Theme.muted)
            }
        } icon: { Image(systemName: icon).foregroundStyle(Theme.primary) }
    }
}

extension Bundle {
    var appVersion: String {
        let v = infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
        let b = infoDictionary?["CFBundleVersion"] as? String ?? "1"
        return "\(v) (\(b))"
    }
}
