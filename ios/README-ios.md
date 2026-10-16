# Manga Vault — iOS

Native SwiftUI app for **Manga Vault** (https://mangas.base44.app). It talks to the same Base44 backend as the web app, so your account, collection, wishlist, XP, cards, events and friends are shared between web and iPhone/iPad.

## Open & run

1. Open `ios/MangaVault.xcodeproj` in Xcode.
2. Pick an iPhone simulator or your device and press **Run**.

No packages — only Apple frameworks (SwiftUI, Charts, PhotosUI, AuthenticationServices).

## Navigation

Bottom tabs like the web app's mobile bar: **Home · Catalog · Collection · Events · More**. Everything else (Wishlist, Friends, Messages, Rewards, Capsules, Cards, …) is on the **More** page. The bell (top right) shows notifications.

## Features

| Area | Screens |
|---|---|
| Account | Login, register + email code, reset password, Google sign-in |
| Home | Level bar, event banner, stats, trading cards / achievements / capsules counters, customizable sections (Continue Reading with **+1 chapter**, Daily Quests, Recent Achievements, Recent Trading Cards, Recently Added, Charts) |
| Catalog & Discover | Full MangaDex catalog, search, publisher filters, event banner, OWNED badges, Surprise me |
| Manga & Volume | All volumes, add / wishlist per volume, series progress, AI synopsis, chapters, **Read on MangaFire** per volume |
| Collection | Statuses incl. **Dropped**, 11 sort options, genre / rating / progress filters, series-progress panel, rating, chapter progress, notes |
| Rewards | Daily reward with streak, level rewards, reward history (server-side XP) |
| Vault Capsules | Open earned capsules with reveal animation, drop rates |
| Trading Cards | Card collection, rarity filter, favorites, free starter pack |
| Events | Active / upcoming / completed events, join, quests, claims, leaderboards, Christmas & Boruto special events, countdowns |
| Quests & Achievements | Daily + weekly quests (claimed on the server), 16 achievements |
| Social | Friends (requests, search), Messages (chat), notifications, public profiles, friends' collections, Manga Room |
| Library | Wishlist, Series Tracker, Reading Calendar + sessions, Journal, Tier Lists, Manga Wrapped, Statistics |
| Discover more | Recommendations, Manga Roulette, Random Manga, Manga Versus |

Admin tools (event editor, card manager) stay on the website.

## Changelog

**1.0.0 (2)**
- New: Rewards, Vault Capsules, Trading Cards, Community Events (incl. Christmas & Boruto Vol. 7 events), Manga Roulette, Manga Room
- New: Friends, Messages, notifications, friends' collections
- New: Reading Calendar, Journal, Tier Lists, Series Tracker, Recommendations, Random Manga, Manga Versus
- XP, levels, achievements and quest rewards now come from the server (same totals as the website)
- Weekly quests, 4 extra achievements
- Home: customizable sections, +1 chapter button, cards/capsules counters, event banner
- Collection: Dropped status, more sorting, genre/rating/progress filters, series progress panel
- Volume page: per-volume MangaFire link; manga page: series progress
- Profile: favourite manga, characters and genres; add friend / message from public profiles
- New tab bar: Home · Catalog · Collection · Events · More

**1.0.0 (1)** — first version

## Notes

- **Google sign-in** returns to the app via `mangavault://auth`. If Base44 refuses a custom-scheme return URL, use email + password.
- Deep link to a public profile: `mangavault://u/<profileId>`.
- Realtime updates of the website (chat, notifications) are done with light polling in the app.
