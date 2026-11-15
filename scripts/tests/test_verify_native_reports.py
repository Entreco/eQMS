import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verify_native_reports import EXPECTED, verify


class NativeReportVerificationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)

    def write_report(self, cases, outcome=None):
        suite = ET.Element("testsuite")
        for classname, name in cases:
            case = ET.SubElement(suite, "testcase", classname=classname, name=name)
            if outcome:
                ET.SubElement(case, outcome)
        ET.ElementTree(suite).write(self.directory / "TEST-native.xml")

    def test_all_required_tests_pass(self):
        self.write_report(sorted(EXPECTED))
        verify(self.directory)

    def test_no_reports_fail(self):
        with self.assertRaisesRegex(ValueError, "No native integration"):
            verify(self.directory)

    def test_missing_expected_test_fails(self):
        self.write_report(sorted(EXPECTED)[1:])
        with self.assertRaisesRegex(ValueError, "Missing expected"):
            verify(self.directory)

    def test_failed_error_or_skipped_test_fails(self):
        for outcome in ("failure", "error", "skipped"):
            with self.subTest(outcome=outcome):
                self.write_report(sorted(EXPECTED), outcome)
                with self.assertRaisesRegex(ValueError, "failed or skipped"):
                    verify(self.directory)

    def test_duplicate_test_fails(self):
        cases = sorted(EXPECTED)
        self.write_report(cases + cases[:1])
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            verify(self.directory)

    def test_malformed_report_fails(self):
        (self.directory / "TEST-native.xml").write_text("<invalid")
        with self.assertRaises(ET.ParseError):
            verify(self.directory)

    def test_missing_module_reports_fail(self):
        self.write_report(sorted(EXPECTED))
        with self.assertRaisesRegex(ValueError, "No native integration"):
            verify(self.directory, self.directory / "missing-module")

    def test_suite_failure_summary_fails(self):
        self.write_report(sorted(EXPECTED))
        report = self.directory / "TEST-native.xml"
        suite = ET.parse(report)
        suite.getroot().set("failures", "1")
        suite.write(report)
        with self.assertRaisesRegex(ValueError, "suite failed or skipped"):
            verify(self.directory)


if __name__ == "__main__":
    unittest.main()
