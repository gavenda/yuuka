#!/usr/bin/env python3
"""
Cuts every image of Yuuka the two apps ship from the source art in `branding/`.

    python3 scripts/brand_assets.py        # needs Pillow

`branding/` is not in the repository (it is the game's own artwork, kept untracked); what this writes is.
The crops are decided here and nowhere else, so a change of framing is a change to this file and a re-run:

- a portrait per mood, for `YuukaPortrait` in both apps: her bust, from her halo down to the foot of the badge
  it is shown in. The badge is each app's to draw, in the theme's secondary container, and to mask her with:
  it cuts her off at its scalloped edge from its middle down, and her head, bow and halo stand clear above it.
  So the art here is only a rectangle, with no colour or edge of its own, and suits either theme.
- the logo and every icon: her face on the pyjamas' blue, the same canvas the Android adaptive icon uses.
"""

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
EXPRESSIONS = ROOT / "branding" / "expressions"
ANDROID_RES = ROOT / "app" / "src" / "main" / "res"
WEB_PUBLIC = ROOT / "webapp" / "public"

# A mood's name in both apps (`YuukaMood`, `lib/yuuka.ts`) and the numbered expression it is cut from. Adding one
# here, and to those two, is all a new mood takes.
MOODS = {
    "neutral": 1,
    "attentive": 2,
    "stern": 5,
    "worried": 6,
    "gentle": 7,
    "pleased": 13,
    "uneasy": 15,
    "asleep": 99,
}
# Moods only the web shows, for a screen Android has no counterpart of (the sign-in failure card).
WEB_ONLY = {"worried"}

# The portrait, in the source art's own pixels: the box it is cut from, and the badge both apps draw in it,
# which is as wide as the box and sits on its bottom edge. The badge's middle is just under her nose.
BUST_LEFT, BUST_RIGHT = 80, 700
BADGE_Y, BADGE_RADIUS = 430, (BUST_RIGHT - BUST_LEFT) // 2
BUST_BOTTOM = BADGE_Y + BADGE_RADIUS

# The icons. Her halo crosses the tip of her bow, so the art starts just under it: an icon is her face.
ICON_ART_TOP = 80
# The square of the art an icon shows, which is the middle two thirds of the adaptive icon's canvas. It is cut
# as `branding/reference-icon.png` is: her face close, the headband and bow out of view.
ICON_LEFT, ICON_TOP, ICON_SIDE = 231, 161, 355
ICON_BACKGROUND = "#9EBCD8"  # SecondarySeed in ui/theme/Color.kt: the pyjamas, in shade
ADAPTIVE_CANVAS, ADAPTIVE_VISIBLE = 648, 432


def expression(number: int) -> Image.Image:
    return Image.open(EXPRESSIONS / f"Yuuka_Pajamas_Portrait_Expression_{number}.webp").convert("RGBA")


def portrait(number: int) -> Image.Image:
    # At the art's own size, 620 by 740: an empty state draws her large, and there is no more detail to be had.
    return expression(number).crop((BUST_LEFT, 0, BUST_RIGHT, BUST_BOTTOM))


def icon_canvas() -> Image.Image:
    """The adaptive icon's foreground: her face in the middle two thirds of a transparent canvas."""
    art = expression(MOODS["neutral"])
    ImageDraw.Draw(art).rectangle([0, 0, art.width, ICON_ART_TOP - 1], fill=(0, 0, 0, 0))

    # The canvas is half as wide again as what an icon shows, around the same middle.
    margin = ICON_SIDE * (ADAPTIVE_CANVAS - ADAPTIVE_VISIBLE) // (2 * ADAPTIVE_VISIBLE)
    side = ICON_SIDE + 2 * margin
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.alpha_composite(art, (margin - ICON_LEFT, margin - ICON_TOP))
    return canvas.resize((ADAPTIVE_CANVAS, ADAPTIVE_CANVAS), Image.LANCZOS)


def on_background(art: Image.Image) -> Image.Image:
    backed = Image.new("RGBA", art.size, ICON_BACKGROUND)
    backed.alpha_composite(art)
    return backed.convert("RGB")


def sized(image: Image.Image, side: int) -> Image.Image:
    return image.resize((side, side), Image.LANCZOS)


def write(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.suffix == ".webp":
        image.save(path, "WEBP", quality=88, method=6)
    else:
        image.save(path, "PNG", optimize=True)
    print(f"{path.relative_to(ROOT)}  {image.width}x{image.height}  {path.stat().st_size // 1024} KB")


def main() -> None:
    for mood, number in MOODS.items():
        cut = portrait(number)
        write(cut, WEB_PUBLIC / "yuuka" / f"{mood}.webp")
        if mood not in WEB_ONLY:
            write(cut, ANDROID_RES / "drawable-nodpi" / f"yuuka_{mood}.webp")

    foreground = icon_canvas()
    whole = on_background(foreground)
    inset = (ADAPTIVE_CANVAS - ADAPTIVE_VISIBLE) // 2
    face = whole.crop((inset, inset, inset + ADAPTIVE_VISIBLE, inset + ADAPTIVE_VISIBLE))

    # Android: the adaptive icon's foreground (its background is `ic_launcher_background` in colors.xml) and the
    # mark the rail and the sign-in screen draw.
    write(foreground, ANDROID_RES / "drawable-nodpi" / "ic_launcher_foreground.webp")
    write(sized(face, 384), ANDROID_RES / "drawable-nodpi" / "yuuka_logo.png")

    # The web: the same mark, and the icons a browser and an installed PWA ask for. A maskable icon keeps the
    # whole canvas, since its safe zone is the adaptive icon's.
    write(sized(face, 384), WEB_PUBLIC / "yuuka.png")
    write(sized(face, 48), WEB_PUBLIC / "favicon.png")
    write(sized(face, 180), WEB_PUBLIC / "apple-touch-icon.png")
    write(sized(face, 192), WEB_PUBLIC / "pwa-192.png")
    write(sized(face, 512), WEB_PUBLIC / "pwa-512.png")
    write(sized(whole, 512), WEB_PUBLIC / "pwa-maskable-512.png")


if __name__ == "__main__":
    main()
