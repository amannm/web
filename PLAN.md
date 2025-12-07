Snapshot optimization takeaways (from reference/):
- Keep computedStyles minimal and valid to avoid Chrome crashes; request only the 10 fields downstream code reads.
- Convert layout bounds from device pixels to CSS pixels using devicePixelRatio before returning.
- Build a snapshot-index → layout-index map up front to avoid O(n²) lookups.
- Cap processed iframes to max_iframes and log truncation to prevent memory/CPU blowups.
- Disable costly extras (blended backgrounds, text color opacities); keep paint order and DOM rects enabled for visibility logic.
- Read iframe scroll offsets first via Runtime.evaluate and reuse them when composing coordinates.
- Run CDP calls for snapshot, DOM tree, AX tree, and DPR in parallel with short retries to reduce wall-clock time while failing fast.
