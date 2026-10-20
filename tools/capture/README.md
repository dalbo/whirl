# Fixture capture

Whirlpool sits behind a Cloudflare managed challenge that rejects curl, Node fetch, and headless Chromium,
but passes a headed Chromium window. These scripts drive a headed Playwright Chromium (works under WSLg) to save
real pages and API responses into `fixtures/`, which the parser unit tests and API models are written against.

    cd tools/capture && npm install && npx playwright install chromium
    npm run public                      # public HTML pages listed in public.json
    printf '%s' 'YOUR-KEY' > ~/.config/whirldroid/apikey && chmod 600 ~/.config/whirldroid/apikey
    npm run api                         # JSON API calls (key read from the file, never printed or saved)
    npm run actions                     # watchedadd/read/remove on 3pxj858k: CHANGES the key owner's watched list, ask first

Files in `fixtures/html/*.html` are the RAW server response (what OkHttp + jsoup will see); `fixtures/html/rendered/` holds the
post-JavaScript DOM for reference only. Parsers must be written against the raw files.

`api.json` is generated and contains the key; it is git-ignored. Captured responses contain the capturing
user's name, ID, and watched-thread list, so re-capture with your own key rather than editing by hand.
Set `HEADLESS=1` to try headless first (as of 2026-10-20 it is challenged and the script falls back to headed).

`npm run actions` saves the two bogus-id calls as `api_cmd_watched*_bogus.json`; they are an HTML error page, so they were
kept once as `api_cmd_bogus_id_500.html`.
