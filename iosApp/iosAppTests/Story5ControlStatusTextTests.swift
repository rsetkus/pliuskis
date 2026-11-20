import Shared
import XCTest
@testable import iosApp

/// STORY-5, AC30 and AC31: the control screen's display text is a Swift mapping of the shared meaning values
/// (`HumidityStatus`, `WaterLevelStatus`, the reading's `KotlinInstant`). Kotlin decides thirsty/good and low/enough
/// (the thresholds live in `DeviceStatusMapper`); Swift only turns each value into words and the time into a date for
/// a locale and time zone.
///
/// Production API these tests pin (iosApp target):
/// ```swift
/// enum ControlStatusText {
///     static func healthTitle(_ status: HumidityStatus) -> String
///     static func healthMessage(_ status: HumidityStatus) -> String
///     static func humidity(_ status: HumidityStatus) -> String       // soil moisture caption
///     static func waterLevel(_ status: WaterLevelStatus) -> String   // water tank caption
///     static func percent(_ value: Int32, locale: Locale) -> String
///     /// The reading time: only the time for a reading from today, else day, month and time; for `locale` and in
///     /// `timeZone`. ControlScreen passes the SwiftUI environment's `\.locale` and `\.timeZone`.
///     static func updatedAt(_ instant: KotlinInstant, locale: Locale, timeZone: TimeZone, now: Date) -> String
/// }
/// ```
/// `HumidityStatus` and `WaterLevelStatus` are the Swift enums SKIE generates for the Kotlin enums (`CaseIterable`),
/// so `allCases` is every Kotlin value: a value added in Kotlin shows up here.
///
/// "Swift contains no humidity or water-level threshold numbers" (AC31) is checked by review and recorded in the
/// story's implementation map.
final class Story5ControlStatusTextTests: XCTestCase {

    /// 2026-11-19T10:15:00Z.
    private static let lastUpdate = KotlinInstant.companion.fromEpochMilliseconds(epochMilliseconds: 1_795_083_300_000)
    private static let utc = TimeZone(identifier: "UTC")!
    /// UTC+2 on 2026-11-19 (no daylight saving time in November).
    private static let vilnius = TimeZone(identifier: "Europe/Vilnius")!
    private static let britishEnglish = Locale(identifier: "en_GB")

    // AC31
    // GIVEN every HumidityStatus value Kotlin defines
    // WHEN Swift maps each to its display text
    // THEN each has a non-empty text, and no two values share a text
    func testStory5_everyHumidityStatusHasItsOwnDisplayText() {
        XCTAssertFalse(HumidityStatus.allCases.isEmpty)

        let texts = HumidityStatus.allCases.map { ControlStatusText.humidity($0) }

        for (status, text) in zip(HumidityStatus.allCases, texts) {
            XCTAssertFalse(
                text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                "HumidityStatus.\(status) has no display text"
            )
        }
        XCTAssertEqual(Set(texts).count, texts.count, "Two humidity values share a display text: \(texts)")
    }

    // AC31
    // GIVEN every WaterLevelStatus value Kotlin defines
    // WHEN Swift maps each to its display text
    // THEN each has a non-empty text, and no two values share a text
    func testStory5_everyWaterLevelStatusHasItsOwnDisplayText() {
        XCTAssertFalse(WaterLevelStatus.allCases.isEmpty)

        let texts = WaterLevelStatus.allCases.map { ControlStatusText.waterLevel($0) }

        for (status, text) in zip(WaterLevelStatus.allCases, texts) {
            XCTAssertFalse(
                text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                "WaterLevelStatus.\(status) has no display text"
            )
        }
        XCTAssertEqual(Set(texts).count, texts.count, "Two water level values share a display text: \(texts)")
    }

    // AC30
    // GIVEN HumidityStatus THIRSTY and WaterLevelStatus LOW
    // WHEN Swift maps them
    // THEN the texts say so: the health card says thirsty, the moisture caption says dry, the tank caption asks for a
    //   refill
    func testStory5_thirstyAndLowTextsSayThirstyAndLow() {
        XCTAssertTrue(
            ControlStatusText.healthTitle(.thirsty).localizedCaseInsensitiveContains("thirsty"),
            "THIRSTY title was \"\(ControlStatusText.healthTitle(.thirsty))\""
        )
        XCTAssertTrue(
            ControlStatusText.humidity(.thirsty).localizedCaseInsensitiveContains("dry"),
            "THIRSTY caption was \"\(ControlStatusText.humidity(.thirsty))\""
        )
        XCTAssertTrue(
            ControlStatusText.waterLevel(.low).localizedCaseInsensitiveContains("refill"),
            "LOW caption was \"\(ControlStatusText.waterLevel(.low))\""
        )
    }

    // GIVEN each HumidityStatus value
    // WHEN Swift maps it to the health card's title and message
    // THEN the two states read differently
    func testStory5_healthCardTextsDifferPerHumidityStatus() {
        XCTAssertNotEqual(ControlStatusText.healthTitle(.thirsty), ControlStatusText.healthTitle(.good))
        XCTAssertNotEqual(ControlStatusText.healthMessage(.thirsty), ControlStatusText.healthMessage(.good))
    }

    // GIVEN a reading percentage
    // WHEN Swift formats it for en_GB
    // THEN it is a whole percentage
    func testStory5_readingIsFormattedAsWholePercentage() {
        XCTAssertEqual(ControlStatusText.percent(72, locale: Self.britishEnglish), "72%")
    }

    // AC30
    // GIVEN lastUpdate 2026-11-19T10:15:00Z, locale en_GB, and "now" two days later
    // WHEN Swift formats it in UTC and in Europe/Vilnius (UTC+2)
    // THEN both show the day (19) and the wall-clock time of their own zone: 10:15 in UTC, 12:15 in Vilnius
    func testStory5_updateTimeIsFormattedInTheGivenTimeZone() {
        let now = Date(timeIntervalSince1970: 1_795_256_100) // 2026-11-21T10:15:00Z
        let inUtc = ControlStatusText.updatedAt(Self.lastUpdate, locale: Self.britishEnglish, timeZone: Self.utc, now: now)
        let inVilnius = ControlStatusText.updatedAt(
            Self.lastUpdate, locale: Self.britishEnglish, timeZone: Self.vilnius, now: now
        )

        XCTAssertTrue(inUtc.contains("10:15"), "UTC text was \"\(inUtc)\"")
        XCTAssertTrue(inUtc.contains("19"), "UTC text has no day: \"\(inUtc)\"")
        XCTAssertTrue(inVilnius.contains("12:15"), "Vilnius text was \"\(inVilnius)\"")
        XCTAssertFalse(inVilnius.contains("10:15"), "Vilnius text shows the UTC time: \"\(inVilnius)\"")
        XCTAssertTrue(inVilnius.contains("19"), "Vilnius text has no day: \"\(inVilnius)\"")
    }

    // GIVEN lastUpdate 2026-11-19T10:15:00Z and "now" later the same day in UTC
    // WHEN Swift formats it
    // THEN only the time shows, as on Android
    func testStory5_updateTimeFromTodayShowsOnlyTheTime() {
        let laterToday = Date(timeIntervalSince1970: 1_795_101_300) // 2026-11-19T15:15:00Z

        let text = ControlStatusText.updatedAt(
            Self.lastUpdate, locale: Self.britishEnglish, timeZone: Self.utc, now: laterToday
        )

        XCTAssertEqual(text, "10:15")
    }

    // AC30
    // GIVEN lastUpdate 2026-11-19T10:15:00Z in UTC, not from today
    // WHEN Swift formats it for en_US and for de_DE
    // THEN the texts differ: the format follows the locale, not a fixed pattern
    func testStory5_updateTimeIsFormattedForTheGivenLocale() {
        let now = Date(timeIntervalSince1970: 1_795_256_100)
        let american = ControlStatusText.updatedAt(
            Self.lastUpdate, locale: Locale(identifier: "en_US"), timeZone: Self.utc, now: now
        )
        let german = ControlStatusText.updatedAt(
            Self.lastUpdate, locale: Locale(identifier: "de_DE"), timeZone: Self.utc, now: now
        )

        XCTAssertFalse(american.isEmpty)
        XCTAssertFalse(german.isEmpty)
        XCTAssertNotEqual(american, german)
    }
}
