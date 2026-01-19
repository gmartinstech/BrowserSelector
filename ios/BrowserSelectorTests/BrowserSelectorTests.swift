import XCTest
@testable import BrowserSelector

final class BrowserSelectorTests: XCTestCase {

    func testBrowserKnownBrowsersNotEmpty() throws {
        XCTAssertFalse(Browser.knownBrowsers.isEmpty)
    }

    func testSafariIsAlwaysInstalled() throws {
        let safari = Browser.knownBrowsers.first { $0.id == "safari" }
        XCTAssertNotNil(safari)
        XCTAssertTrue(safari?.isInstalled ?? false)
    }

    func testBrowserHasRequiredFields() throws {
        for browser in Browser.knownBrowsers {
            XCTAssertFalse(browser.id.isEmpty, "Browser ID should not be empty")
            XCTAssertFalse(browser.name.isEmpty, "Browser name should not be empty")
            XCTAssertFalse(browser.urlFormat.isEmpty, "Browser URL format should not be empty")
        }
    }
}
