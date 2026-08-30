# V3.0.1 Mobile UI + Safety Audit

Checked before repackaging:

- Android targetSdk 35 system-bar/gesture inset handling added.
- Bottom Home / Activity / Files / Settings bar is outside scroll content and padded above Android navigation controls.
- Extra side/bottom margin added to bottom navigation.
- Compact layout path added for <=380dp phone widths.
- Header, hero shield, transfer arc, storage text and connection row shrink on compact phones.
- Header bell is functional (opens Activity); no dead decorative action.
- Continuous avatar animators removed; finite entrance animation remains.
- Animated ShieldPulseView already cancels on detach.
- Idle dashboard refresh slowed; active transfer remains fast-refresh.
- Existing-file migration requires explicit confirmation when enabling.
- Same Video Duplicate Guard is mandatory and no longer user-disableable.
- Duplicate decision remains exact-size candidate + quick fingerprint + full SHA-256 confirmation.
- Cleanup revalidation now also re-checks destination quick fingerprint before source deletion.
- Repeated transfer failures use bounded retry backoff rather than immediate endless retries.
- XML resources parse successfully.
- Java sources were syntax-screened with javac; full Android compile still requires GitHub Actions/Android SDK.

Golden rule: if destination/source identity cannot be proven, the original is kept.
