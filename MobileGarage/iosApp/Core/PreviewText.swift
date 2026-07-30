import SwiftUI

/// Sample text for `#Preview` fixtures.
///
/// Fixture text is not shipped copy and must never be translated, but a literal
/// written directly into a `LocalizedStringResource` parameter *is* a literal in
/// resource position, so the compiler extracts it — which is how
/// `"Since 11:22 AM · 38 min"` and ~20 other sample strings ended up in the
/// String Catalog.
///
/// Routing the sample through a runtime `String` means the compiler never sees a
/// literal there, so nothing is extracted. The rendered result is identical: an
/// unknown key falls back to itself.
///
/// Production code must NOT use this — it defeats the extraction that the rest of
/// the localization work exists to enable. It is for `#Preview` bodies only.
///
/// `internal` on purpose: a `#Preview` body is embedded verbatim into the
/// generated snapshot test, which cannot see `private` symbols.
/// See MobileGarage/docs/IOS_LOCALIZATION.md.
func previewText(_ value: String) -> LocalizedStringResource {
    LocalizedStringResource(stringLiteral: value)
}
