# Play Store Data Safety form — answers

This is the cheat sheet for filling out the **Data Safety** section of the
Play Console for WealthFlow. Reviewers will compare your answers here with
the actual permissions and network calls in the APK, so keep this file in
sync with the code.

## Section 1: Data collection and security

| Question                                            | Answer |
|-----------------------------------------------------|--------|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit?  | **Yes** (HTTPS-only via `network_security_config.xml`) |
| Do you provide a way for users to request that their data be deleted? | **Yes** — in-app via Portfolio → Settings → Delete Data; deletion is also automatic on uninstall |

## Section 2: Data types

### Financial info → User payment info
- **Collected:** Yes
- **Shared:** No
- **Processing:** Processed ephemerally _(only the Angel One client code/PIN/TOTP is sent, just to Angel One's own API; nothing is sent to a WealthFlow-controlled server because there isn't one)_
- **Required or optional:** Optional (the user opts in by configuring Angel One)
- **Purpose:** App functionality
- **Encrypted in transit:** Yes
- **User can request deletion:** Yes

### Financial info → Other financial info (transactions, holdings)
- **Collected:** Yes (stored locally)
- **Shared:** No
- **Required or optional:** Required for app functionality
- **Purpose:** App functionality
- **Encrypted in transit:** Yes (TLS to Angel One; SMS data never leaves the device)
- **User can request deletion:** Yes

### Messages → SMS or MMS
- **Collected:** Yes (read in-memory, not persisted as SMS)
- **Shared:** No
- **Required or optional:** Optional — user can deny and use manual entry
- **Purpose:** App functionality
- **Encrypted in transit:** N/A — never leaves the device
- **User can request deletion:** Yes (deleting transactions discards anything derived from SMS)

### Device or other IDs → Device or other IDs
- **Collected:** Yes — local IPv4 only, sent to Angel One per their API requirements
- **Shared:** Yes (with Angel One)
- **Purpose:** App functionality

## Section 3: SMS / Call Log Permissions Declaration

Because we declare `READ_SMS` and `RECEIVE_SMS`, Google requires a separate
**Permissions Declaration** form (Console → App content → Sensitive
permissions). Use the following script:

> **Use case:** WealthFlow uses SMS access to automatically detect
> transaction notifications from bank short codes (alphanumeric IDs such as
> `AD-HDFCBK`, `VM-SBIINB`) so users can track their spending without
> manual entry.
>
> **Why this is core functionality:** Automatic transaction logging is the
> single advertised value proposition of the app. Without it, the user
> would have to manually enter every transaction.
>
> **Why no alternative API works:** India does not yet have a widely
> adopted Account Aggregator deployment for retail bank statements. The SMS
> Retriever API only works for app-bound OTP messages (it requires the
> sending app's hash) and cannot read bank notifications.
>
> **Data handling:** SMS contents are parsed entirely on-device. Only the
> extracted amount, type and category are written to a local Room
> database. Raw SMS bodies are kept on the device only as the
> `Transaction.message` field for the user's own reference and are never
> transmitted off-device.

> ⚠️ **Realistic outlook:** Google has rejected this justification for
> almost every "expense tracker" app since 2019. If the declaration is
> denied, your remediation path is to remove the SMS permissions from
> the manifest and ship a manual-entry-only version. The receiver and
> parser code is feature-flagged at the manifest level (delete the
> `<receiver>` element and the two `<uses-permission>` lines), so removing
> the feature is a one-commit change.

## Section 4: Account deletion declaration

Per the 2024 account-deletion policy, declare:

- **Where users can delete their data:** In-app — Portfolio → Settings →
  Delete Data.
- **Web URL for deletion requests:** Not applicable (no online accounts).
- **Data retained after deletion:** None.
