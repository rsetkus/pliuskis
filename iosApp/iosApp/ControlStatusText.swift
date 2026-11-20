import Foundation
import Shared

/// The control screen's display text for the meaning values of the shared `DeviceStatus`, the same words as Android's
/// control screen. Kotlin decides whether the plant is thirsty and the water is low (the thresholds live in
/// `DeviceStatusMapper`); Swift only puts each value into words and formats the reading time for the user's locale and
/// time zone.
enum ControlStatusText {

    /// The health card's title.
    static func healthTitle(_ status: HumidityStatus) -> String {
        switch status {
        case .thirsty:
            String(localized: "Thirsty", comment: "Health card title: the soil is dry")
        case .good:
            String(localized: "Feeling great", comment: "Health card title: soil humidity is healthy")
        }
    }

    /// The health card's one-line explanation.
    static func healthMessage(_ status: HumidityStatus) -> String {
        switch status {
        case .thirsty:
            String(localized: "The soil is dry. Water soon.", comment: "Health card message: the soil is dry")
        case .good:
            String(localized: "Soil moisture is just right.", comment: "Health card message: soil is healthy")
        }
    }

    /// The soil moisture tile's caption.
    static func humidity(_ status: HumidityStatus) -> String {
        switch status {
        case .thirsty:
            String(localized: "Dry", comment: "Soil moisture caption: below the healthy level")
        case .good:
            String(localized: "Healthy", comment: "Soil moisture caption: healthy")
        }
    }

    /// The water tank tile's caption.
    static func waterLevel(_ status: WaterLevelStatus) -> String {
        switch status {
        case .low:
            String(localized: "Refill soon", comment: "Water tank caption: the tank is running low")
        case .enough:
            String(localized: "Full enough", comment: "Water tank caption: no refill needed")
        }
    }

    /// A reading as a whole percentage ("72%"), for `locale`.
    static func percent(_ value: Int32, locale: Locale) -> String {
        (Double(value) / 100).formatted(.percent.precision(.fractionLength(0)).locale(locale))
    }

    /// The reading time: only the time ("12:15") for a reading from today, the day, month and time ("19 Nov, 12:15")
    /// for an older one, as on Android. For `locale` and in `timeZone`; `now` decides what "today" is.
    /// `ControlScreen` passes the SwiftUI environment's `\.locale` and `\.timeZone`.
    static func updatedAt(_ instant: KotlinInstant, locale: Locale, timeZone: TimeZone, now: Date = .now) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(instant.toEpochMilliseconds()) / 1000)
        var calendar = locale.calendar
        calendar.timeZone = timeZone
        let style = calendar.isDate(date, inSameDayAs: now)
            ? Date.FormatStyle(locale: locale, calendar: calendar, timeZone: timeZone).hour().minute()
            : Date.FormatStyle(locale: locale, calendar: calendar, timeZone: timeZone).day().month(.abbreviated).hour().minute()
        return date.formatted(style)
    }
}
