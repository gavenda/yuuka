---
paths:
  - "**/Banks.kt"
  - "**/AccountLogo.kt"
  - "**/accounts/**"
  - "webapp/src/lib/banks.ts"
  - "webapp/public/banks/**"
  - "app/src/main/res/drawable/bank_*"
  - "banks/**"
---

# Account logos and preset banks

- **Account logos are linked, not uploaded**: An account may carry an image URL the
client loads from wherever it lives — no upload, no copy, no storage beyond the
string. The scheme is restricted to http(s), because the value ends up in an
image load and anything else invites `data:` payloads and similar surprises. A
URL that fails to load falls back to the account's initial. Inverting the logo in
dark mode is a per-account opt-in (`logoInvertDark`), not a blanket filter — a
dark mark on a transparent background needs it to stay visible, but a colour logo
inverted the same way would come out wrong.

- **A preset bank is a key, and everything else about it is code**: The account form's Bank field, after
Type, lists the banks both apps ship with and then Custom. An account stores only the chosen bank's key
(`accounts.bank`); its name, its logo (with a dark-mode one where the bank has one, `*_light.svg` and
`*_dark.svg`) and whether a single logo inverts in dark mode live in `webapp/src/lib/banks.ts` and
`ui/common/Banks.kt`. The web serves the SVGs from `webapp/public/banks/`; Android draws them as vector
drawables (`res/drawable/bank_*.xml`) converted from the same files, with the SVG's styles inlined first
(svgo's `inlineStyles` and `convertStyleToAttrs`) or class-based fills come out empty, and shapes turned into
paths (`convertShapeToPath`) or a `<polygon>` or `<rect>` is silently dropped; `banks/` at the root
is where they arrive,
so changing a bank reaches every account that names it — the same reason an account references its type by
id. Only Custom shows the logo URL and the invert switch; choosing a preset clears both on save. The server
checks only the key's shape (a lowercase slug), so adding a bank is a release in both apps, never a
migration, and a key one app does not know yet falls back to the initial.
