# Layouts and colours

Settings › General has two independent choices: **Layout** (how screens draw) and **Colours** (the palette). Each is one JSON file, named after its `id`; adding one is adding a file, no code changes unless a layout needs a switch that does not exist yet.

- Layouts: `core/ui/src/main/assets/layouts/` — `classic` (default, must stay), `cards` (design by Jkill).
- Palettes: `core/ui/src/main/assets/palettes/` — `whirl` (Whirl blue, must stay), `whirlpool` (the website's colours), `silver-ember` (design by Jkill). "From your wallpaper" (Material You, Android 12+) is built in, not a file, and is the default; v1.0's "Use wallpaper colours" off becomes Whirl blue.

## Palette

```json
{
  "id": "whirlpool",
  "name": "Whirlpool",
  "credit": "optional, shown after the name",
  "light": { "primary": "#223399", "surface": "#EEEEEE", "topBar": "#3A437B", "onTopBar": "#FFFFFF" },
  "dark":  { "primary": "#A9B0F0", "surface": "#1D1F2B", "topBar": "#2B3160", "onTopBar": "#FFFFFF" }
}
```

- Colours by Material 3 role name (`primary`, `onPrimary`, `primaryContainer`, `secondaryContainer`, `tertiary`, `background`, `surface`, `surfaceContainerLow`…`Highest`, `onSurfaceVariant`, `outlineVariant`, … every `ColorScheme` role except the fixed ones). Roles left out keep Material's baseline. `#RRGGBB` or `#AARRGGBB`.
- Whirl's roles: `unread` (unread row tint in layouts that tint rows; without it, a wash of `primary`), `quoteRepliedUser` and `quoteOther` (default: Whirlpool's blue and brown), `topBar` and `onTopBar` (top app bars; without them, Material's surface colour). The status bar icons follow `topBar`'s lightness.
- Where things sit: flat posts on `background`, cards on `surface`, boxed quotes on `surfaceContainerLow`, the moderator note and post author band on `surfaceContainerHigh`, sticky rows on `secondaryContainer`, the note's label in `tertiary`.

## Layout

```json
{ "id": "cards", "name": "Cards", "credit": "design by Jkill",
  "layout": { "corner": 18, "gutter": 16, "lists": "cards", "posts": "cards", "quotes": "boxed",
              "headings": "marker", "tabs": "pills", "unread": "accent", "boldTitles": true } }
```

| Switch | Values |
|---|---|
| `corner`, `gutter` | Card corner radius and space between cards and the screen edge, in dp. |
| `lists` | `flat` (full-width rows) or `cards` (each section of a thread list, the forums list and Settings is one outlined card). |
| `posts` | `flat` (dividers) or `cards` (each post an outlined card, author on a deeper band). |
| `quotes` | `text` (coloured quote text) or `boxed` (a wholly quoted paragraph in a recessed box with a coloured rule). |
| `headings` | `band` (tinted band, primary colour) or `marker` (plain text after a small accent bar). |
| `tabs` | Watched's Unread/All: `underline` (Material tabs) or `pills` (a track with the unread count). |
| `unread` | `badge` (bold title, count badge) or `accent` (tinted row, accent bar, "N new"). |
| `boldTitles` | Semibold screen titles and headings. |

## Checks

`StylesTest` (`:core:ui`) loads every file: unknown roles, bad colours, a `topBar` without `onTopBar` fail, and with **every palette in every layout**, light and dark, text, accents and quotes must reach 4.5:1 on the surfaces they sit on (top bar text and subtitles included). Check a new palette or layout on a phone in light, dark and at 200 % font size before merging.
