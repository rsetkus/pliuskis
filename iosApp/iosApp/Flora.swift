import SwiftUI

/// The Flora design tokens on iOS: the same hues and meanings as Android's Material 3 theme
/// (`core/designsystem` `theme/Color.kt`), from the asset catalog's color sets, each with a dark variant.
/// See `docs/design-system.md`.
enum Flora {
    /// Healthy, online, positive.
    static let leaf = Color("Leaf")
    static let leafContainer = Color("LeafContainer")
    static let onLeafContainer = Color("OnLeafContainer")
    /// Water: the water action and the water tank.
    static let rain = Color("Rain")
    static let onRain = Color("OnRain")
    /// Needs attention soon: dry soil, low tank. Never used for failures; those are red.
    static let clay = Color("Clay")
    static let clayContainer = Color("ClayContainer")
    static let onClayContainer = Color("OnClayContainer")
    /// The screen background behind cards.
    static let canvas = Color("Canvas")
    /// Cards and reading tiles.
    static let card = Color("Card")
    /// The empty part of a reading's meter.
    static let track = Color("Track")

    /// Spacing on a 4 pt grid, the same steps as Android's `Spacing`.
    enum Spacing {
        static let xxs: CGFloat = 4
        static let xs: CGFloat = 8
        static let sm: CGFloat = 12
        static let md: CGFloat = 16
        static let lg: CGFloat = 24
    }

    /// Corner radii: tiles and rows, and the larger health card.
    enum Radius {
        static let tile: CGFloat = 16
        static let healthCard: CGFloat = 28
    }
}

extension View {
    /// A Liquid Glass capsule behind a small piece of chrome (a status line, the offline banner) on iOS 26, tinted
    /// with `tint`; a thin material capsule before iOS 26. Apply after padding, so the glass wraps the padded label.
    @ViewBuilder
    func floraGlassCapsule(tint: Color? = nil) -> some View {
        if #available(iOS 26, *) {
            glassEffect(tint.map { Glass.regular.tint($0) } ?? .regular, in: Capsule())
        } else {
            background(.thinMaterial, in: Capsule())
                .overlay(Capsule().fill(tint ?? .clear).allowsHitTesting(false))
        }
    }

    /// The screen's main action: a prominent Liquid Glass button tinted `tint` on iOS 26, a bordered prominent button
    /// before it. Both are capsules.
    @ViewBuilder
    func floraProminentButton(tint: Color) -> some View {
        if #available(iOS 26, *) {
            buttonStyle(.glassProminent)
                .tint(tint)
        } else {
            buttonStyle(.borderedProminent)
                .buttonBorderShape(.capsule)
                .tint(tint)
        }
    }
}
