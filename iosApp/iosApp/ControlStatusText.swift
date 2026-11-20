import Foundation
import Shared

/// The control screen's display text for the meaning values of the shared `DeviceStatus`. Kotlin decides whether the
/// plant is thirsty and the water is low (the thresholds live in `DeviceStatusMapper`); Swift only puts each value
/// into words and formats the reading time for the user's locale and time zone.
enum ControlStatusText {

    static func humidity(_ status: HumidityStatus) -> String {
        switch status {
        case .thirsty:
            String(localized: "Thirsty, needs water", comment: "Soil humidity: below the healthy level")
        case .good:
            String(localized: "Well watered", comment: "Soil humidity: healthy")
        }
    }

    static func waterLevel(_ status: WaterLevelStatus) -> String {
        switch status {
        case .low:
            String(localized: "Running low", comment: "Water tank level: needs a refill")
        case .enough:
            String(localized: "Enough water", comment: "Water tank level: no refill needed")
        }
    }

    /// The reading time as date and time, for `locale` and in `timeZone`. `ControlScreen` passes the SwiftUI
    /// environment's `\.locale` and `\.timeZone` (the device's settings unless a parent overrides them).
    static func updatedAt(_ instant: KotlinInstant, locale: Locale, timeZone: TimeZone) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(instant.toEpochMilliseconds()) / 1000)
        let style = Date.FormatStyle(
            date: .abbreviated,
            time: .shortened,
            locale: locale,
            calendar: locale.calendar,
            timeZone: timeZone
        )
        return date.formatted(style)
    }
}
