"""Reject missing, failed or skipped native integration coverage (EQMS-127)."""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

EXPECTED = {
    ("com.entreco.eqms.desktop.DesktopWindowNativeTest", "nativeMouseClickReachesTheUnifiedTitleBarAction"),
    ("com.entreco.eqms.desktop.DesktopWindowTest", "productionHostUnifiesHeaderAndKeepsWindowAndWorkspaceAcrossThemes"),
    ("com.entreco.eqms.desktop.DesktopNativeMenuTest", "realWindowInstallsMenuOrderAndNativeApplicationHandlers"),
    ("com.entreco.eqms.desktop.DesktopJewelPreviewTest", "realNativeWorkspaceRendersInBothThemes"),
    ("com.entreco.eqms.desktop.DesktopAboutIntegrationTest", "actualAboutDialogShowsAccessibleLogoAndVersionInBothThemes"),
    ("com.entreco.eqms.feature.scaffold.ScaffoldAboutIntegrationTest", "settingsProjectChooserAndAboutDoNotToggleBackgroundTools"),
    ("com.entreco.eqms.feature.scaffold.ScaffoldAboutIntegrationTest", "aboutWindowKeepsEditorUsableAndApplicationExitGuarded"),
    ("com.entreco.eqms.feature.scaffold.DesktopChromeIntegrationTest", "aboutIsOneNativeDecoratedNonModalWindowWithIndependentLifecycle"),
    ("com.entreco.eqms.feature.scaffold.DesktopChromeIntegrationTest", "invalidInitialPreferenceIsVisibleAndAboutShowsBuildVersion"),
}


def verify(*directories: Path) -> None:
    reports = []
    for directory in directories:
        module_reports = sorted(directory.glob("TEST-*.xml"))
        if not module_reports:
            raise ValueError("No native integration XML reports; missing tests are not passing coverage.")
        reports.extend(module_reports)
    observed = set()
    for report in reports:
        suite = ET.parse(report).getroot()
        if any(int(suite.get(outcome, "0")) > 0 for outcome in ("failures", "errors", "skipped")):
            raise ValueError(f"Native suite failed or skipped: {suite.get('name')}")
        for case in suite.iter("testcase"):
            identity = (case.get("classname"), case.get("name"))
            if identity in observed:
                raise ValueError(f"Duplicate native test: {identity}")
            observed.add(identity)
            if any(case.find(outcome) is not None for outcome in ("failure", "error", "skipped")):
                raise ValueError(f"Native test failed or skipped: {identity}")
    missing = EXPECTED - observed
    if missing:
        raise ValueError(f"Missing expected native tests: {sorted(missing)}")


def main() -> int:
    if len(sys.argv) < 2:
        print("Usage: verify_native_reports.py <integrationTest XML directories...>", file=sys.stderr)
        return 2
    try:
        verify(*(Path(argument) for argument in sys.argv[1:]))
    except (ValueError, OSError, ET.ParseError) as error:
        print(f"Native coverage verification failed: {error}", file=sys.stderr)
        return 1
    print(f"All {len(EXPECTED)} required native integration tests executed without failures or skips.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
