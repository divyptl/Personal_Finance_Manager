# WealthFlow Privacy Policy

_Last updated: 2026-04-11_

WealthFlow ("the App") is a personal finance manager that runs entirely on
your device. This policy explains what data the App handles, why, and what
your rights are. It is written to satisfy the requirements of the Google
Play Developer Program Policies and the Indian Digital Personal Data
Protection Act, 2023.

## TL;DR

- The App stores everything locally on your phone. **There is no WealthFlow server.**
- The App talks to **Angel One's API** when you choose to sync your broker account.
- The App reads bank notification SMS **on your device only** to log
  transactions automatically. SMS contents are never transmitted off-device.
- You can wipe everything from inside the App via _Portfolio → Settings →
  Delete Data_, or simply by uninstalling.

## Data we handle

| Category                | Where it lives                                            | Why                              |
|------------------------|-----------------------------------------------------------|----------------------------------|
| Bank transactions      | Local Room database (`wealth_database`)                   | Display recent transactions      |
| Stock holdings         | Local Room database                                       | Show your portfolio              |
| Angel One client code  | `EncryptedSharedPreferences` (AES-256, Android Keystore)  | Authenticate with Angel One      |
| Angel One PIN          | `EncryptedSharedPreferences`                              | Authenticate with Angel One      |
| Angel One JWT token    | `EncryptedSharedPreferences`                              | Skip re-login for ~23 hours      |
| SMS messages           | Read from system, parsed in memory, **never persisted as raw SMS** | Detect transaction notifications |
| Device IPv4 address    | In-memory only (sent in headers to Angel One per their API requirements) | Required by broker API |

## Data we **do not** collect

- We do **not** collect your name, email, phone number, or any account
  identifier outside what you explicitly enter for Angel One.
- We do **not** use analytics, crash reporting, or advertising SDKs.
- We do **not** share data with third parties (except Angel One, when you
  explicitly initiate a sync).
- We do **not** read your SMS history — only inbound bank-format SMS that
  arrive while the App is installed.

## Permissions we request

| Permission             | Why                                                                                     |
|------------------------|-----------------------------------------------------------------------------------------|
| `RECEIVE_SMS` / `READ_SMS` | Detect transaction notifications from bank short codes (e.g. `AD-HDFCBK`).            |
| `INTERNET`             | Sync your portfolio with Angel One.                                                     |
| `POST_NOTIFICATIONS`   | (Android 13+) Notify you when a new transaction is detected.                            |

You can deny SMS access and still use manual transaction entry.

## Third parties

The only third party the App contacts is **Angel One Smart API**
(`apiconnect.angelbroking.com`). When you initiate a sync, the App sends:
- your client code, PIN, and 2FA TOTP code (for the login call only),
- the resulting JWT token in subsequent API calls, and
- the standard headers Angel One's API requires.

Angel One's privacy policy applies to data they receive:
<https://www.angelone.in/privacy-policy>.

## Security

- Credentials are stored in `EncryptedSharedPreferences` with an AES-256-GCM
  master key held in the Android Keystore.
- All network traffic uses HTTPS. Cleartext HTTP is blocked at the network
  security configuration level.
- Release builds are minified and obfuscated with R8 to reduce attack
  surface against reverse engineering.
- The SMS receiver is restricted to senders holding the
  `BROADCAST_SMS` permission, which only the OS itself does — preventing
  other installed apps from spoofing fake bank SMS into the parser.
- Auto-backup is disabled and credential preferences are also explicitly
  excluded from cloud backup as a defense-in-depth measure.

## Your rights — Data deletion

You can delete all locally-stored data at any time:
1. Open the App.
2. Go to **Portfolio → Settings (gear icon) → Delete Data**.
3. Confirm.

This will permanently remove all transactions, holdings, credentials, and
the JWT token from your device. Uninstalling the App also removes all data.

If you have synced holdings to Angel One, deleting from WealthFlow does
**not** delete anything from your Angel One account — that data is owned by
Angel One and you must contact them to delete it.

## Children

The App is not directed at children under 13 and does not knowingly collect
data from them.

## Changes to this policy

Material changes will be reflected by an updated "Last updated" date at the
top of this document. The current version is always available at the URL
linked from the Play Store listing.

## Contact

For privacy questions, file an issue in the project repository or email the
maintainer listed in the Play Store listing.
