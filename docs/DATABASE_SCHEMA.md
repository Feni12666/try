# Room schema plan

Room is introduced with the indexing engine, not in the UI-only foundation.
The planned entities are:

- `storage_volume`: stable phone/USB identity and capability flags.
- `indexed_file`: unified file identity, name, location, size and timestamps.
- `video_metadata`: duration, resolution, codec and thumbnail cache key.
- `content_hash`: quick fingerprint, SHA-256 state and byte verification state.
- `perceptual_fingerprint`: sampled frame hashes for similar-video scans.
- `duplicate_group`: exact/similar group type and confidence.
- `duplicate_member`: group membership and user review state.
- `transfer_rule`: source, target, copy/move mode and auto-run conditions.
- `transfer_job`: persistent transfer state and verification progress.
- `operation_history`: auditable copy, move, rename and delete outcome.
- `scan_session`: scan scope, progress, pause state and completion result.

Migrations will be tested from the first production schema onward. No actual
video bytes are ever stored in Room.
