# Sharing a session (A20/A21)

## The approved origin

**`https://cuwano.gramos.me/`** — confirmed by the user and verified from this machine before
it was written down:

| Check | Result |
|---|---|
| `GET /` | `200`, 17,599 bytes |
| `GET /?chords=Cmaj` | `200`, 28,208 bytes — the query is read, not ignored |
| `GET /?chords=Cmaj&chords=Amin` | `200` |
| `GET /?chords=C%23maj` | `200` — the percent-encoded sharp, the one spelling a raw `#` would break |

While the origin was unresolved (`DEC-07`) this was `null` and sharing was off with a sentence
instead of a guessed host, which is what the plan asked for in the meantime. Now the value is
in `ShareConfig`, the note that explained its absence disappears with it (the note was driven
by `unavailableReason`, so there is nothing to remove by hand), and a non-HTTPS base is still
refused like no base at all — the tests in `app/src/test/.../ShareLauncherTest.kt` pin both.

## What works today, and where it comes from

- **Importing**, both halves of `A19`: an `ACTION_SEND` text/plain intent (cold start and
  `onNewIntent`), and the explicit `Pegar enlace` button. The engine reads the link with
  `import_legacy_url` — any origin, the page route only, the legacy codec's tolerance.
- **The emitting half of the codec** is built and pinned: `encode_page_query` turns a page
  into the query that reproduces it, with the spelling the frozen transport accepts (`#` as
  `%23`, a space as `+`, `,` literal, `%` as `%25`), and the round trip back through
  `import_legacy_url` is asserted for a chord page, a marked position, a sharp root, a
  non-default tuning and the piano (core PR #38/#39, artifact 0.8.0, API revision 8).
- The engine's answer deliberately carries **no base and no `?`**: the origin belongs to the
  application, and this is the only place a base is joined to it.

## Approving an origin later

1. Set `ShareConfig.approvedBase` to the HTTPS origin and path prefix the web app serves
   (the path matters: the engine serves only the page route `/`).
2. The `unavailableReason` disappears with it, so the note in the UI goes away and the share
   control can send the link through the system chooser (`ACTION_SEND`, `text/plain`).
3. `A21` then decides the App Link association: verified `ACTION_VIEW` only after the domain
   owner publishes `assetlinks.json` for this application id, and never with a wildcard host
   filter. Until then **no `ACTION_VIEW` filter is declared at all**, which is also why the
   application does not appear as a handler for web links.
