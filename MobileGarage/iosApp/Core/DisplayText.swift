import SwiftUI

/// Text bound for the screen: either translatable copy, or data shown as-is.
///
/// The distinction matters because a plain `String` erases it, and the two need
/// opposite handling. Copy must reach the String Catalog or it can never be
/// translated; data must never be looked up in the catalog, because translating
/// a person's name, a formatted date, or a version number is meaningless at best
/// — and a value that happened to collide with a catalog key would be silently
/// rewritten into something else.
///
/// Deliberately NOT `ExpressibleByStringLiteral`. That would let call sites keep
/// writing bare literals, but the literal would arrive as a plain `String`
/// through `init(stringLiteral:)` and never be extracted — reintroducing exactly
/// the silent failure this type exists to prevent, while looking tidier.
///
/// See MobileGarage/docs/IOS_LOCALIZATION.md.
enum DisplayText {
    /// Translatable copy. Write the literal inline, so the compiler sees a
    /// `LocalizedStringResource` and extracts it.
    case copy(LocalizedStringResource)

    /// A value rendered exactly as given — a name, an email address, an
    /// already-locale-formatted date, a version string.
    case data(String)

    /// Renders as a `Text`.
    ///
    /// Not `@ViewBuilder`: that would produce `_ConditionalContent`, and callers
    /// apply `Text`-only modifiers such as `.font`.
    var view: Text {
        switch self {
        case .copy(let resource): return Text(resource)
        case .data(let string): return Text(verbatim: string)
        }
    }
}
