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
///     static func humidity(_ status: HumidityStatus) -> String
///     static func waterLevel(_ status: WaterLevelStatus) -> String
///     /// The reading time as date and time, for `locale` and in `timeZone`. ControlScreen passes the SwiftUI
///     /// environment's `\.locale` and `\.timeZone` (the device's settings unless a parent overrides them).
///     static func updatedAt(_ instant: KotlinInstant, locale: Locale, timeZone: TimeZone) -> String
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
    // THEN the texts say so: the humidity text mentions thirst and the water level text says the water is low
    func testStory5_thirstyAndLowTextsSayThirstyAndLow() {
        XCTAssertTrue(
            ControlStatusText.humidity(.thirsty).localizedCaseInsensitiveContains("thirsty"),
            "THIRSTY text was \"\(ControlStatusText.humidity(.thirsty))\""
        )
        XCTAssertTrue(
            ControlStatusText.waterLevel(.low).localizedCaseInsensitiveContains("low"),
            "LOW text was \"\(ControlStatusText.waterLevel(.low))\""
        )
    }

    // AC30
    // GIVEN lastUpdate 2026-11-19T10:15:00Z, locale en_GB
    // WHEN Swift formats it in UTC and in Europe/Vilnius (UTC+2)
    // THEN both show the date (19, 2026) and the wall-clock time of their own zone: 10:15 in UTC, 12:15 in Vilnius
    func testStory5_updateTimeIsFormattedInTheGivenTimeZone() {
        let inUtc = ControlStatusText.updatedAt(Self.lastUpdate, locale: Self.britishEnglish, timeZone: Self.utc)
        let inVilnius = ControlStatusText.updatedAt(Self.lastUpdate, locale: Self.britishEnglish, timeZone: Self.vilnius)

        XCTAssertTrue(inUtc.contains("10:15"), "UTC text was \"\(inUtc)\"")
        XCTAssertTrue(inUtc.contains("19"), "UTC text has no day: \"\(inUtc)\"")
        XCTAssertTrue(inUtc.contains("2026"), "UTC text has no year: \"\(inUtc)\"")
        XCTAssertTrue(inVilnius.contains("12:15"), "Vilnius text was \"\(inVilnius)\"")
        XCTAssertFalse(inVilnius.contains("10:15"), "Vilnius text shows the UTC time: \"\(inVilnius)\"")
        XCTAssertTrue(inVilnius.contains("2026"), "Vilnius text has no year: \"\(inVilnius)\"")
    }

    // AC30
    // GIVEN lastUpdate 2026-11-19T10:15:00Z in UTC
    // WHEN Swift formats it for en_US and for de_DE
    // THEN the texts differ: the format follows the locale, not a fixed pattern
    func testStory5_updateTimeIsFormattedForTheGivenLocale() {
        let american = ControlStatusText.updatedAt(
            Self.lastUpdate, locale: Locale(identifier: "en_US"), timeZone: Self.utc
        )
        let german = ControlStatusText.updatedAt(
            Self.lastUpdate, locale: Locale(identifier: "de_DE"), timeZone: Self.utc
        )

        XCTAssertFalse(american.isEmpty)
        XCTAssertFalse(german.isEmpty)
        XCTAssertNotEqual(american, german)
    }

    // AC30
    // GIVEN the same instant, locale and time zone
    // WHEN Swift formats it twice
    // THEN the text is the same: it depends only on its inputs (no hidden device setting)
    func testStory5_updateTimeDependsOnlyOnItsInputs() {
        let first = ControlStatusText.updatedAt(Self.lastUpdate, locale: Self.britishEnglish, timeZone: Self.vilnius)
        let second = ControlStatusText.updatedAt(
            KotlinInstant.companion.fromEpochMilliseconds(epochMilliseconds: 1_795_083_300_000),
            locale: Locale(identifier: "en_GB"),
            timeZone: TimeZone(identifier: "Europe/Vilnius")!
        )

        XCTAssertEqual(first, second)
    }
}
