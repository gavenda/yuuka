# Branding

yuuka is named after Hayase Yuuka (早瀬ユウカ), the accountant of Millennium Science School's Seminar in the game Blue Archive, as she is in her pyjamas once the books are closed. What makes the app look and sound like itself is taken from her: its colours are her clothes, her face is on its empty screens and in About, the print on her pyjamas is its backdrop, and its text is written as an accountant keeping the user's books.

This file records what is used, where, and the rules that hold it together, as built on top of yuuka 3.2.1. It holds for both apps, the Android one and the web one, which are kept the same; where a thing is named twice below, the first name is Android's and the second the web's.

## In short

1. **The theme is the app's own.** Three colours, two of them sampled from her clothes. The wallpaper's colours are not taken up, and there is no setting for them.
2. **Yuuka speaks as "I", to "you".** She never says "Sensei", and she makes no cat puns: the cats are on her pyjamas, not in her mouth.
3. **Her face is for a screen with nothing to show, the end of the transaction list, About, the foot of the form for a new transaction, account, category, tag or subscription, and the sign-out dialog, where she is 144 wide and uneasy, as if asking you to stay.** It is never beside a figure, in any other form, a chart or any other dialog.
4. **A badge masks her.** She is cut off at the scalloped edge of a badge in her pyjamas' blue, and her head, bow and halo stand clear above it.
5. **The cat print is a backdrop**, behind the screens that have nothing else on them and faintly in a few fixed places.
6. **Every image comes from one script**, `scripts/brand_assets.py`, so the two apps cannot drift and no image is edited by hand.

## Names

The app and the character have the same name, told apart by the capital.

**yuuka** is the app, as it writes itself: the name over the sign-in button, and "yuuka 3.2.1" in About (`brand_name`; `common.appName`). The Android launcher labels it "Yuuka" (`app_name`), as the web manifest's full name does, since there it is a name in a list of names.

**Yuuka** is the character. Where the text has someone doing the work, it is her, and she says "I".

## Who Yuuka is

Her profile gives the facts, the card that introduced her pyjama outfit gives her in a sentence, and her [Relationship Story](<https://bluearchive.wiki/wiki/Yuuka_(Pajama)/Relationship_Story>) on the Blue Archive wiki shows how she behaves. The story's scripts are on that page's subpages, `/1` to `/4`.

- She is a second-year at Millennium Science School and the accountant of its Seminar. She is 16, her birthday is 14 March, and her hobby is "calculating things".
- Her card says: "An excellent accountant is always ready for a good night's sleep!" She does her duties perfectly and on time, and chronic insomnia and a school that will not leave her alone keep her from the rest she has dressed for.
- She is exact. She counts things out loud to the last one, and reaches for numbers to settle a question.
- She is strict about money and about what is proper. An appeal about expenses is to be made in writing, and a spendthrift gets told.
- She is easily flustered, and when caught out she argues her own indulgences as sums: "It's more of a loss to NOT buy it!"
- She lies awake going over the day: "I could've done better today. How can I improve tomorrow?" What she settles on is "I'm sure I can do even better tomorrow."
- Under the strictness she is kind.

[Voice](#voice) says which of these the text draws on.

## Colour

The app has one theme, its own, the same on every device and in the browser, in light and in dark. `YuukaTheme` in `ui/theme/Theme.kt` has MaterialKolor's core build it from three colours kept in `ui/theme/Color.kt`, in the tonal spot style, to the 2026 colour spec, at default contrast.

| Input | Value | Taken from | Becomes |
|---|---|---|---|
| Seed | `#415BA3` | Chosen before her art was in the project. | `primary`, the surfaces and `error` |
| Pyjama blue | `#9EBCD8` | Her pyjamas, where the fabric is in shade | `secondary` |
| Bow pink | `#EDB0D0` | The bows on her slippers | `tertiary` |

The seed goes through the style, which picks its hue's tones. The other two are given as they are, so their palettes keep their own hue and chroma. These are the roles as MaterialKolor 6.0.0 resolves them:

| Role | Light | Dark |
|---|---|---|
| `primary` | `#4E5D8C` | `#BAC5EE` |
| `primaryContainer` | `#BBCAFF` | `#465174` |
| `secondary` | `#45627B` | `#ACCAE6` |
| `secondaryContainer` | `#CCE5FF` | `#203E55` |
| `tertiary` | `#81506C` | `#FFCCE5` |
| `tertiaryContainer` | `#F8BADB` | `#F8BADB` |
| `surface` | `#FAF8FE` | `#0D0E12` |

A later version of the library may move these by a shade. The three inputs are what is fixed.

The web app has no generator. Its roles in `webapp/src/style.css` are this output written out, light and dark, and are regenerated from the same library when an input changes, never edited one at a time.

- **Blue (primary)** is the default accent: filled buttons, an amount coming in, the outline of the cat print.
- **Pyjama blue (secondary)** is an amount moving between the user's own accounts, a filter that is on, the card the spending chart is drawn on, and the badge behind Yuuka's portrait. The launcher and web icons sit on the input itself.
- **Pink (tertiary)** is for what is worth a glance without being good or bad: a budget near its limit, a day of spending above the average, the count on a filter button. It is also the ears of the cat print, as on the fabric.
- **A connected button group** takes all three by position: the first button is primary, the second secondary, the third tertiary, and round again. A button rests in its colour's container and fills with the colour when selected. The component does it (`ConnectedButtonGroup`), so no caller passes a colour.
- **The display mode buttons** in Settings are such a group: following the system is the first and is its icon alone, then Light and Dark with an icon and a word each.

In the dark theme `tertiary` and `tertiaryContainer` are nearly the same pink, so a selected third button there is told from its neighbours by its shape, which rounds off into a pill, more than by its colour.

Every colour drawn comes from the theme. Two are written out as Android resources, and the web has their counterparts:

| Resource | Value | For |
|---|---|---|
| `@color/ic_launcher_background` | `#9EBCD8` | Behind her face on the launcher icon |
| `@color/window_background` | `#FAF8FE` in light, `#0D0E12` in dark | The window before the first frame; the web's `theme-color` is the same pair |

Type, shapes and motion are Material 3 Expressive's, with `MotionScheme.expressive()`. The web bundles Google Sans Flex. The app has no wordmark beyond its name in that type.

## The icon and the logo

The icon is her face and her bow on the pyjamas' blue. Her halo is left off: it crosses the tip of her bow, so the art starts just under it.

- **Android.** An adaptive icon: `ic_launcher_foreground.webp`, a 648px canvas with her face in the middle two thirds, over `@color/ic_launcher_background`. It has no themed (monochrome) layer.
- **The logo**, on the rail and the sign-in screen, is that middle square on its own (`yuuka_logo.png`; `yuuka.png`), always drawn as a circle.
- **The web.** `favicon.png`, `apple-touch-icon.png`, `pwa-192.png` and `pwa-512.png` are the logo at their sizes. `pwa-maskable-512.png` is the whole canvas, since a maskable icon's safe zone is the adaptive icon's.

## Interface icons

Every icon in the interface is a Material Symbol (Outlined, weight 400), taken from Google's `material-design-icons` repository and never drawn by hand.

- **Filled by default.** An icon is the filled glyph. The one exception is navigation: a destination is outlined until it is the current one and filled while it is (`iconSelected`), in the bottom bar, the rail and the drawer. A destination's icon used anywhere that is not navigation (a setting's row) is its filled glyph. A toggle that opens something follows the same rule as a destination: the Transactions filter buttons are outlined while their row of chips is shut and filled while it is open.
- **Android.** One vector drawable per symbol in `res/drawable`, named `<symbol>_48px.xml`, as in kozeki, and drawn with `painterResource(R.drawable.…)`. A destination also has `<symbol>_fill_48px.xml` for its filled glyph, and `<symbol>_48px.xml` is its outline. The files are set to 24dp with no tint, so `Icon` sizes and colours them; the directional ones (arrows, logout, menu open) are `autoMirrored`. A parameter that takes an icon is a `@DrawableRes Int`.
- **The web.** The same symbols' paths in `lib/icons.ts`, in the symbols' `0 -960 960 960` box, drawn by `AppIcon`. The names are the symbol's, in capitals; a destination has `NAME` (outlined) and `NAME_FILL`.
- **A new icon** is the same symbol on both sides, added to the two places above in the same change.

## Yuuka's portraits

`YuukaPortrait(mood)` (`ui/common/YuukaPortrait.kt`; `YuukaPortrait.vue` with `lib/yuuka.ts`) shows Yuuka from the shoulders up in a badge: twelve scallops, the hem of her pyjamas, in `secondaryContainer`. The badge masks her. From its middle down she is cut off at its edge, and her head, her bow and her halo stand clear above it. Android draws the badge as Material's twelve-sided cookie and the web as its own scalloped path, and each cuts her with the shape it draws. She is 192 wide unless given another width, and is hidden from a screen reader, as the words beside her say what the screen has to.

She is in a few kinds of place and nowhere else.

**A screen with nothing to show.** `EmptyState` requires a `mood`. A smaller message inside a card, such as a month with nothing recorded on the dashboard, is plain text with no face.

**The end of the transaction list.** Once there are no more to load she is asleep under the last row, 96 wide, with "That's every last one, all accounted for." in a speech bubble beside her, so the last row is not mistaken for a page that stopped short.

**A new item's form.** The forms for a new transaction, account, category, tag or subscription end with her (`YuukaSays`: her portrait at 96 wide with a line in a speech bubble). Editing shows nothing there, and the account type's small dialog has no room for her.

| Form | Mood | Line |
|---|---|---|
| Transaction | `Attentive` | Every coin goes in the book, and I count it twice. |
| Account | `Attentive` | Tell me what it holds, and I'll count from there. |
| Category | `Neutral` | Name it, and I'll file every coin under it. |
| Tag | `Pleased` | Tags don't change my sums. They only help you find things. |
| Subscription | `Stern` | Another one? I'm keeping a running total, and I'm not impressed. |

**About.** The last thing in Settings, with no card and no heading: her `Gentle` face at 96, a speech bubble beside it with her line, and "yuuka" with the version underneath. Under the version are two plain lines in the third person: "Kept by Yuuka, the Seminar's accountant", with her name alone in `primary`, and, quieter, "Character design by Owa, illustration by Paruru, from Blue Archive" with Owa in `secondary` and Paruru in `tertiary`. On 14 March the bubble says "It's my birthday today. A month within budget would be present enough." instead. Her speech bubble (`SpeechBubble`) is used only here, at the end of the transaction list and and in the form for a new transaction, account, category, tag or subscription.

Eight faces are cut, and a new empty state picks from them first. The source number is the `<n>` of `branding/expressions/Yuuka_Pajamas_Portrait_Expression_<n>.webp`.

| Mood | Shows | Source | Used for |
|---|---|---|---|
| `Neutral` | Her usual level look | 1 | Categories, with none yet, and a new category's form |
| `Attentive` | Mouth a little open, listening | 2 | Accounts, with none yet, and the foot of a new transaction or account's form |
| `Stern` | A frown | 5 | Budget, with no categories to budget; a new subscription's form |
| `Worried` | Brows up, mouth open | 6 | The web's sign-in failure card, which Android has no screen for, so only the web ships it |
| `Gentle` | A slight smile | 7 | Subscriptions, with none yet; About |
| `Pleased` | A smile, eyes open | 13 | Tags, with none yet, and a new tag's form |
| `Uneasy` | A wavering mouth | 15 | Transactions, with nothing matching |
| `Asleep` | Eyes closed and calm | 99 | The end of the transaction list |

Thirteen more are in the sources and not cut: 3 a closed-eye smile, 4 flustered with a bead of sweat, 8 a small "o", 9 gritted teeth, 10 laughing, 11 a comic yell, 12 dejected, 14 a shy pout, 16 shouting with her eyes shut, 17 a smile under shadowed eyes, 18 smug, 19 panicked and sweating, and 20 embarrassed.

### Cutting a new mood

The sources are 966×2368, all in one pose, so one box serves every face.

1. Add the mood and its source number to `MOODS` in `scripts/brand_assets.py`, and to `WEB_ONLY` if Android has no screen for it.
2. Run `python3 scripts/brand_assets.py`, which needs Pillow. It cuts 620×740 from the top of the source, starting 80px in from the left: the 620px square at the foot is the badge, with its middle just under her nose, and the 120px above it is where her bow and halo stand out. It is saved at the art's own size as WebP, about 53 KB, to `res/drawable-nodpi/yuuka_<mood>.webp` and `webapp/public/yuuka/<mood>.webp`.
3. Add it to `enum class YuukaMood` and to `YuukaMood` in `lib/yuuka.ts`.

The same script writes the logo and every icon, so a change to a crop is a change to the script and a re-run.

## The cat print

The print on her pyjamas, a round head with two pointed ears, redrawn as a vector so that it takes the theme's colours: the outline in `primary` and the ears in `tertiary` (`ui/common/CatPattern.kt`; `lib/cat.ts`, `CatPattern.vue` and `CatMark.vue`, with the same path strings in both apps).

| Where | How |
|---|---|
| The sign-in and loading screens | The whole backdrop, thinning towards the middle |
| The loading screen and Android's splash | One mark, filled, in the middle of the window (`ic_splash_cat`, drawn in as kozeki's halo is, on a 288dp canvas of which the launch screen shows the middle two thirds; `CatIcon`; `CAT`) |
| The sync banner | One small mark, centred over its line |
| Pull to refresh (Android) | The indicator: it slides down as the pull grows and turns while the sync runs |
| The open rail, and Android's phone drawer | A strip at the top (behind the mark and name in the rail, bare in the drawer), and behind the account card |
| The card a screen leads with | One large mark as a watermark in its corner: net worth on the dashboard, Planned on Budget |

It never goes behind a list, a form, a chart or a column of amounts, and it no longer stands in an empty state: Yuuka does.

## Voice

The text reads as an accountant keeping the user's books: exact, a little strict about spending, never unkind, and drowsy where something is paused or done.

### Who is speaking

Where a line has any character in it, Yuuka is speaking, in the first person and to "you": "Tell me your gross monthly pay.", "I checked twice. Nothing.", "I'll need an explanation." She gives one short line of character and then the instruction.

Two lines are about her and so are in the third person: the two under the version in About.

A line is written in `strings.xml` and in `webapp/src/i18n/en/` together.

### What comes from her

- **Exactness.** "I checked twice. Nothing." for a search with no result, "…I'll keep it balanced to the last coin." for the first account, "I've done the maths." on rounding up, "Balance corrected. The books agree again." after an adjustment, and "Something doesn't add up. Try that again?" when a thing fails.
- **Calculating things.** Her hobby, so the app is "Calculating…" while it loads.
- **Strictness about spending.** Over budget is "I'll need an explanation." and within it "Every budget within its limit." A month with nothing spent is "Not a coin spent yet. Keep it up.", spending with no budget is "Spent without a plan", and a budget with no categories is told "then we decide what each is allowed."
- **Sleep.** The line over the sign-in button, "Every coin accounted for. Then a good night's sleep.", is after her card's. A subscription posts "even while you sleep", a paused one is "Napping", and she is asleep at the end of the transaction list.
- **Her birthday.** 14 March, in About's bubble.

### What stays plain

Labels, screen names, field hints that only show a format, validation messages, confirmations and their buttons, the notice that a thing was saved or deleted, and the reason given for an error.

### Left out on purpose

- "Sensei" for the user.
- Cat puns and cat words for money. The copy before this was a cat's ("Total hoard", "The cat sat on the keyboard"), and none of it is kept.
- Her scolding at full volume. She is strict in the app, never loud.

## Source artwork

The originals are in `/branding`, which is git-ignored and so only on the developer's machine.

| File | What it is |
|---|---|
| `Yuuka_Pajamas_Intro.webp` | The card that introduced the outfit, 1000×1773: her line about a good night's sleep, and her profile in a paragraph. |
| `expressions/` | 21 full-length portraits, 966×2368, in one pose with different faces, numbered 1 to 20 and 99. The colours were sampled from number 1. |
| `Yuuka_Halo.webp` | Her halo on its own, 350×350. Not in use: in the app her halo is only ever the one in her portrait. |

What ships is the icon and logo files listed above, and the cut portraits: seven on Android in `res/drawable-nodpi`, eight on the web in `webapp/public/yuuka`. The badge, the speech bubble and the cat print are drawn in code.

Yuuka and this artwork are from Blue Archive. The wiki credits the pyjama outfit's design to Owa and its illustration to Paruru; her original design and illustration are Hwansang's.
