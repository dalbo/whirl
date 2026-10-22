# App styles

Settings › General › App style. Each style is one JSON file in `core/ui/src/main/assets/styles/`, named after its `id`. Adding a style is adding a file; no code changes unless it needs a layout the switches below do not offer.

```json
{
  "id": "silver-ember",
  "name": "Silver & ember",
  "credit": "design by Jkill",
  "ownColours": true,
  "light": { "primary": "#A63D0B", "surface": "#F4F5F7", "unread": "#F1ECE8" },
  "dark": { "primary": "#FFAA72", "surface": "#232A33", "unread": "#2E2B29" },
  "layout": { "corner": 18, "gutter": 16, "lists": "cards", "posts": "cards", "quotes": "boxed",
              "headings": "marker", "tabs": "pills", "unread": "accent", "boldTitles": true }
}
```

| Field | Meaning |
|---|---|
| `id`, `name` | File name and the name shown in Settings. `classic` is the default and must stay. |
| `credit` | Optional; shown after the name in Settings. |
| `ownColours` | The style has a full palette, so choosing it turns off "Use wallpaper colours" (the user can turn it back on; the layout stays and the colours follow the wallpaper). |
| `light`, `dark` | Colours by Material 3 role name (`primary`, `onPrimary`, `primaryContainer`, `surface`, `surfaceContainerHigh`, `outlineVariant`, … every role of `ColorScheme` except the fixed ones). Roles left out keep Material's baseline. `#RRGGBB` or `#AARRGGBB`. |
| Whirl's roles | `unread` (row tint for unread threads; with wallpaper colours it becomes a wash of the wallpaper's primary), `quoteRepliedUser` and `quoteOther` (default: Whirlpool's blue and brown). |
| `layout.corner`, `layout.gutter` | Card corner radius and the space between cards and the screen edge, in dp. |
| `layout.lists` | `flat` (full-width rows) or `cards` (each section of a thread list, the forums list and Settings is one outlined card). |
| `layout.posts` | `flat` (dividers) or `cards` (each post an outlined card, author on a deeper band). |
| `layout.quotes` | `text` (coloured quote text) or `boxed` (a wholly quoted paragraph sits in a recessed box with a coloured rule). |
| `layout.headings` | `band` (tinted band, primary colour) or `marker` (plain text after a small accent bar). |
| `layout.tabs` | Watched's Unread/All: `underline` (Material tabs) or `pills` (a track with the unread count). |
| `layout.unread` | `badge` (bold title, count badge) or `accent` (tinted row, accent bar, "N new"). |
| `layout.boldTitles` | Semibold screen titles and headings. |

`AppStyleTest` (`:core:ui`) loads every file: unknown roles or bad colours fail, and text, accents and quotes must reach 4.5:1 on the surfaces they sit on, in light and dark. Check a new style on a phone in light, dark and at 200 % font size before merging.
