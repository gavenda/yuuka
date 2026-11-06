---
paths:
  - "app/src/main/java/**/ui/**"
  - "webapp/src/**/*.vue"
  - "webapp/src/lib/**"
  - "webapp/src/style.css"
---

# Navigation

- **Navigation follows the window, in both apps**: From 600dp / the `sm` breakpoint up (on Android, any
window width class but Compact, from Material 3 Adaptive's `currentWindowAdaptiveInfoV2`) it is a
rail that opens into a labelled drawer; below that a phone keeps its bottom bar and avatar or
hamburger menu. In both apps the rail lists the same three destinations as the phone's bottom bar
(Transactions, Investments, Accounts), and the app opens on Transactions. The rest sit under three
headings, with no dividers: Insights (Overview, Budget), Automations (Subscriptions, Save the Change)
and Organize (Categories, Tags), then Settings and Sign out without one (`NAV_GROUPS` on Android,
`navGroups` in `lib/nav.ts` on the web). An open rail sits beside the page from 1024 (on Android, an Expanded width class, 840dp) and floats over a scrim
below it, and the open/closed choice is remembered locally — but a narrow window never starts
with it floating. The rail marks the current destination, so a wide window has no top bar; it also carries the
screen's leading action as its FAB, under the menu button (an icon while slim, extended when open).
Settings and Save the Change lend it none: neither screen has a Save, because a choice made
on either is written the moment it is made (see [save-the-change.md](save-the-change.md)). The hide-amounts switch, a month-scoped screen's month
button (below) and the account sit at the foot of the rail. On Android this is `ui/NavRail.kt` (`YuukaNavRail`, a Material 3
`WideNavigationRail`, or its modal variant when floating) hosted by `YuukaApp`; a screen's leading
action goes through `ScreenFab` (`ui/common/RailFab.kt`), which draws the phone's FAB or hands the
action to the rail, so a new screen never places its own. The web side is in
[webapp/CLAUDE.md](../../webapp/CLAUDE.md).

- **A screen opened from a list stands beside it where the page has room**: Settings and Account types are a
list and its detail. Once the page beside the rail is at least 744dp / 744px wide — a 360 list, a detail at
least as wide, and 24 between, Material's list-detail measures — opening Account types puts it beside
Settings instead of over it; narrower, it covers Settings as any screen opened from another does. It is
judged by the page and not the window, so an open rail docked beside an Expanded window can take the second
pane away. There is no placeholder: Settings alone fills the page until something is opened from it. On
Android this is Material 3 Adaptive's `ListDetailSceneStrategy` in `YuukaApp`'s `NavDisplay`, the entries
marked `listPane()` and `detailPane()` and the detail named in `DETAIL_ROUTES`: until one of those is opened the list is stretched across the page (the scaffold's `PaneExpansionState`), and opening it draws the list in beside it within the same scene rather than sliding a new one in; on the web a route names its list as a second, `list` view, which
`App.vue` draws beside it under a container query. A new list-detail pair is marked the same way in both.

- **The month is changed from an icon button after the hide-amounts switch**: Dashboard and Budget each look
at one month, and the button that changes it — a calendar — goes wherever the hide-amounts
switch is, straight after it: in the phone's app bar, and under it at the rail's foot in a window with a rail,
where the open rail labels it with the month on show. Pressing it opens the month picker, a year over its
twelve months with the current one outlined. It is never page content, so there is no switcher card and no
previous/next arrows; the button's accessible name says which month is showing, since an icon cannot. On
Android this is `MonthTopBar` (`ui/common/ScreenTopBar.kt`), which hands the month to the rail
(`RegisterRailMonth`, `ui/common/RailMonth.kt`) when there is no bar; on the web a view places a
`MonthAction` and the shell draws the button (`lib/monthAction.ts`).

- **Transactions keeps its search in the app bar**: On a phone the bar is Material's app bar with search — the
drawer button, the search bar, then the hide-amounts switch. A window with a rail has no
bar, so there Material's search bar stands on its own at the top of the page. Neither is typed in where it
stands: pressing it opens the full-screen search view, where the query is typed and what matches is listed
under the field as Material's plain list items — the day, what it was, where it was and what is noted on it,
the amount at the end — with none of the page's days, grouped shapes or swipes; pressing one opens it. Back,
or the keyboard's search key, returns to the screen with the bar still holding the query and a button to
clear it. The view is a dialog, so Yuuka is not drawn in it: nothing matching is said in plain text. The
filter row under the bar stands in line with it — its buttons the bar's own distance from the edges, so the
account button's icon is under the bar's first and the tag and category icons under its last two — and keeps
to the page's margin where there is no bar (`barAlignedEdgePadding`). On Android this is `MonthSearchTopBar`
(`ui/common/ScreenTopBar.kt`, over Material's `AppBarWithSearch`, `SearchBar` and
`ExpandedFullScreenContainedSearchBar`, which keeps the page's surface and the field a pill, a left chevron at its start and a clear button at its end), and the results are `TransactionResults` in `TransactionsScreen.kt`. A browser
has no search view, so on the web the search is a `SearchField` typed in where it stands: in a phone's bar,
where it has the whole bar while it is typed in — the mark and the actions step aside until it is left — and
from the rail's breakpoint up at the head of the screen's `TopBar`.

- **The transaction list pages itself**: It is not scoped to a month — every transaction, newest first, however
far back they go — and they come fifty at a time, and nothing is pressed
for the next fifty: it is asked for as the end of the list comes within reach — a few rows ahead of it, so it is
usually there before the end is — and again when a page lands with the end still in reach. For as long as there
is more to come the foot of the list says "Calculating…"; once there is not, Yuuka is asleep under the last
row. A page that could not be had leaves the rows above it alone and is said at the foot instead, with "Try
again", and it is not asked for again until that is pressed, a sync lands or the list is replaced by another
filter — so being offline costs one failed request, not one for as long as the end stays in reach. A
page still on its way when a filter changes is dropped rather than added to the list that
replaced its own. The search view's results page the same way. On Android this is `LoadMoreAtEnd` and
`NextPageFooter` in `TransactionsScreen.kt` over `TransactionsViewModel.loadMore`; on the web `useInView`
(`lib/inView.ts`) watches the foot of the list in `TransactionsView.vue` for the store's `loadMore`.

- **Deep in the list, scrolling up turns the leading action into "Back to top"**: Once Transactions is more
than two screens down and is being scrolled up, its FAB — on the phone or in the rail — becomes "Back to top"
with an upward arrow, and pressing it takes the list to its first row. Scrolling down again, or getting back
within one screen of the top, gives it "New transaction" back — lower than where it came on, so it cannot
flicker at the edge. Once pressed it holds until the list is at the top, because the trip up would otherwise
turn it off and on again on the way; scrolling down meanwhile lets go. It is the same button with its action
changed, so the rail keeps its slot rather than folding it away. On Android this is `BackToTopState` in
`TransactionsScreen.kt`, over the list's `lastScrolledBackward`; on the web `useBackToTop`
(`lib/backToTop.ts`), over the window's scroll.

- **An app bar keeps one colour**: Material lifts a bar a tonal step once content has scrolled beneath it;
here a bar stays on the colour it rests on wherever the page is, and so does the search field in it. On
Android a bar with a scroll behaviour takes `steadyTopAppBarColors()` (`ui/common/ScreenTopBar.kt`), and the
app bar with search leaves both of its scrolled colours unspecified; the web's phone bar is always
`bg-background`. A new bar does the same rather than taking Material's default. A screen's bar is also
centre-aligned: Material's `CenterAlignedTopAppBar` on Android (`ScreenTopBar`, `MonthTopBar`, `DetailTopBar`),
small and pinned, never the large collapsing one, and on the web the phone bar's title is centred on the
bar. A form's bar is the exception: a full-screen dialog keeps Material's start-aligned title beside close.

- **The web app is the Android app as a tablet draws it**: From the rail's breakpoint up, a screen in the
browser is its Compose screen in a window with a rail: the same layout, the same colour roles (the scheme
`ui/theme/Theme.kt` generates, written out in `webapp/src/style.css`), the same components and the same
words. Compose is the specification, so a change to how a screen looks on Android is a change to its web
view as well — the two are not allowed to drift. What that means for the web code is in
[webapp/CLAUDE.md](../../webapp/CLAUDE.md).

- **A form takes the screen over**: The transaction, subscription, account, category and tag forms
and the display-currency list open as Material 3 full-screen dialogs
(`FullScreenDialog`, `ui/common/FullScreenDialog.kt`, on Android; `FormDialog.vue` on the web): close where the
drawer button would be, the title, and Save as the bar's one action, bound to the same `form.valid(...)` a save
button always was. Closing a form that no longer holds what it opened with asks before discarding. Material
keeps that shape for a phone, so a window with a rail gets an ordinary dialog of the same content with its
buttons at the foot. Where the page beside the rail has room for a list's width and a 412dp / 412px pane
(796 in all), the form is neither: it is Material's supporting pane, standing at the page's end with the
screen that opened it still usable beside it — the page gives up its width as the pane comes in — and keeps
the full-screen dialog's bar, since it is as tall as the page. The row it is editing is marked as Material
marks a list's selected item (`secondaryContainer`, every corner large; on Categories the highest neutral surface). Pressing another row, or the leading action, opens that
form in its place, asking first if the one there holds changes; back or Escape closes it before leaving the
screen; and deleting the row it is editing closes it. The display currency stays a dialog wherever it opens
(`beside = false`), since Settings may already have Account types beside it. On Android the shell sizes the
pane (`FullScreenDialogPane`, beside `NavDisplay` in `YuukaApp`) and a screen opens a form through
`rememberFormSwitch`, keyed on what it edits; on the web `App.vue` holds `#form-pane`, `FormDialog` teleports
into it, and a view opens a form through `whenDiscardable` (`lib/formPane.ts`). A new form goes through it
rather than a surface of its own.

- **There are no bottom sheets**: What is picked from a short list — the default account, and Save the Change's
destination, category and the accounts that round up — is Material's basic dialog (`SelectionDialog`,
`ui/common/SelectionDialog.kt` on Android; `SelectionDialog.vue` on the web): the title, the rows, and Cancel,
or Done where each press is written as it is made and the dialog stays open for the next. Adjusting a balance
is a basic dialog of its two fields, Budget's planned income and a category's plan are each one of theirs,
opened by pressing the figure, and an account type — which is only its name — is added or renamed in one of
its single field, opened from the Account types screen by its leading action or by pressing a type. The transaction filters open nothing at all: pressing the account, tag or
category button slides a row of that filter's chips down under the filter bar (`FilterChipRow` in both apps),
which scrolls sideways and applies a change at once; pressing the button again puts the row away.

- **A row's actions are behind a swipe**: A list row shows no buttons of its own: pressing it is its main
action, and what else can be done to it — delete, archive, adjust, pause — is revealed, as filled icon buttons (error-coloured when it destroys), by dragging the row
left (`SwipeToRevealActions` on Android, `SwipeReveal.vue` on the web, where a held mouse drags as a finger
does and the keyboard reaches the actions by Tab). Deleting what would take history with it, or cannot be
undone from the list, asks first in a dialog; taking a label or a category off does not.

- **A confirmation says what it will do, and its buttons answer the question**: Deleting an account, a
transaction or transfer, or a subscription, discarding a changed form and signing out each ask first, in
Material's basic dialog with an icon above a centred headline (`AlertDialog`'s `icon` on Android; the `icon`
prop of `AlertDialog.vue` on the web). The headline is the question, and the body is never empty: it says what
goes with the action — the subscriptions that post to an account, both sides of a transfer, the unsent
changes a sign-out would lose — and that it cannot be undone when it cannot. The buttons are worded as
answers to that question rather than as bare verbs: "Yes, delete it" and "No, go back", "Yes, discard them"
and "No, keep editing", "Yes, sign out" and "No, stay signed in" — never "OK", "Cancel" or a lone "Delete",
so each can be read on its own without the headline. The icon and the confirming button take the error
colour when the answer destroys something, and the declining one stays primary; a sign-out with nothing
unsent destroys nothing, so it keeps the ordinary colours. The words are the client's own: a message the API
wrote for a caller is not shown in a dialog. Both apps use the same words, and a new confirmation follows
the same shape. A dialog that is fields or a list rather than a question — adjusting a balance, naming an
account type, a selection — keeps a start-aligned headline, no icon, and Cancel.
