# App Links (A21): the file to publish, and why the filter is still off

`A21` needs a **verified** HTTPS association. The plan is explicit that publishing is the
domain owner's job and not code to implement here, and that the `ACTION_VIEW` filter must not
be declared until the association is verified — so what follows is the file, ready to publish,
and the checks that come after it. **Declaring the filter first would be claiming an
association this application does not have.**

## The file

Publish exactly this at **`https://cuwano.gramos.me/.well-known/assetlinks.json`**, served as
`application/json`, over HTTPS, with no redirect:

```json
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "dev.ironjanowar.fretboard",
      "sha256_cert_fingerprints": [
        "98:78:5D:6B:9B:F0:0F:14:40:50:6F:AC:EC:75:0B:03:4F:1C:AA:41:1C:72:FD:B9:6F:CA:38:AF:86:93:A9:49"
      ]
    }
  }
]
```

The fingerprint is the **release** certificate's, read from the delivered APK with
`apksigner verify --print-certs` — the one signed `CN=Fretboard Release, OU=Ironjanowar,
O=Ironjanowar, C=ES`, SHA-256 `98785d6b9bf00f1440506facec750b034f1caa411c72fdb96fca38af8693a949`.

Two things about it that matter:

- **the debug certificate is deliberately absent.** `A21` says reinstallation with another
  signer must never be the test shortcut, and it is right: an association that also trusts the
  debug key would be verified by a build nobody shipped.
- **the key is what identifies the application, not the APK.** This fingerprint stays valid for
  every future build signed with the same key. If the signing key is ever rotated, this file has
  to be republished with the new fingerprint, and a hosted Play App Signing key would add *its*
  fingerprint here as well.

## After publishing

1. Add the `ACTION_VIEW` filter to `app/src/main/AndroidManifest.xml` for `https` on that host,
   with **no wildcard host filter** — the plan forbids one, and a wildcard is precisely the
   claim that cannot be verified.
2. Install the **release** APK on a device and check the association is actually verified, not
   merely declared:

   ```bash
   adb shell pm get-app-links dev.ironjanowar.fretboard
   ```

   The domain must report `verified` (and, for a link to open the application without a chooser,
   the app should pass `adb shell pm set-app-links-user-selection` only if the user chooses so —
   that is their device preference, not a build step).
3. Open a real link in a browser and confirm it opens the application **with the session the
   link describes**: that is the end-to-end check `A21` asks for, and it cannot be replaced by
   the manifest merely containing a filter.
4. Until step 2 reports `verified`, treat the association as **deferred** — which the plan
   explicitly allows — and keep the filter off. Paste and `ACTION_SEND` import (A19) and sharing
   (A20) do not depend on any of this.
