/**
 * The faces Yuuka makes, named for what she is feeling rather than for the art they are cut from: the twin of
 * `YuukaMood` in `YuukaPortrait.kt`, plus `worried`, which only the sign-in failure card shows and Android has no
 * screen for. Each is a portrait in `public/yuuka/`, written by `scripts/brand_assets.py` at the repository's
 * root, which is where a new one is added first.
 */
export type YuukaMood = 'neutral' | 'attentive' | 'stern' | 'worried' | 'gentle' | 'pleased' | 'uneasy' | 'asleep';
