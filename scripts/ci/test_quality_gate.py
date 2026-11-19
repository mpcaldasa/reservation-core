"""Exercise failure modes that must never produce a successful merge gate."""

from pathlib import Path
import tempfile
import unittest

from quality_gate import evaluate


class QualityGateTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.target = Path(self.directory.name)
        self.write("surefire-reports/TEST-example.xml", '<testsuite tests="3" failures="0" errors="0" skipped="0"/>')
        self.write("site/jacoco/jacoco.xml", '<report><counter type="LINE" covered="90" missed="10"/><counter type="BRANCH" covered="60" missed="40"/></report>')
        self.write("spotbugsXml.xml", '<BugCollection><Errors errors="0" missingClasses="0"/><BugInstance priority="2"/></BugCollection>')
        self.write("pmd.xml", '<pmd xmlns="http://pmd.sourceforge.net/report/2.0.0"><file><violation/></file></pmd>')
        self.write("cpd.xml", '<pmd-cpd><duplication/></pmd-cpd>')

    def write(self, name, content):
        path = self.target / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)

    def test_boundary_coverage_and_review_findings_pass(self):
        errors, summary = evaluate(self.target)
        self.assertEqual([], errors)
        self.assertIn("90.00%", summary)
        self.assertIn("1 findings", summary)

    def test_missing_and_malformed_reports_fail(self):
        for content in (None, "<broken"):
            with self.subTest(content=content):
                path = self.target / "pmd.xml"
                if content is None:
                    path.unlink()
                else:
                    path.write_text(content)
                self.assertTrue(evaluate(self.target)[0])

    def test_empty_failed_and_skipped_test_runs_fail(self):
        for attributes in ('tests="0" failures="0" errors="0" skipped="0"', 'tests="3" failures="1" errors="0" skipped="0"', 'tests="3" failures="0" errors="1" skipped="0"', 'tests="3" failures="0" errors="0" skipped="1"'):
            with self.subTest(attributes=attributes):
                self.write("surefire-reports/TEST-example.xml", f"<testsuite {attributes}/>")
                self.assertTrue(evaluate(self.target)[0])

    def test_both_coverage_regressions_fail(self):
        for line, branch in ((89, 60), (90, 59)):
            with self.subTest(line=line, branch=branch):
                self.write("site/jacoco/jacoco.xml", f'<report><counter type="LINE" covered="{line}" missed="{100-line}"/><counter type="BRANCH" covered="{branch}" missed="{100-branch}"/></report>')
                self.assertTrue(evaluate(self.target)[0])

    def test_incomplete_coverage_fails(self):
        self.write("site/jacoco/jacoco.xml", '<report><counter type="LINE" covered="0" missed="0"/></report>')
        self.assertTrue(evaluate(self.target)[0])

    def test_high_priority_and_incomplete_spotbugs_fail(self):
        for content in ('<BugCollection><Errors errors="0" missingClasses="0"/><BugInstance priority="1"/></BugCollection>', '<BugCollection><Errors errors="1" missingClasses="0"/></BugCollection>', '<BugCollection><Errors errors="0" missingClasses="1"/></BugCollection>', '<BugCollection/>'):
            with self.subTest(content=content):
                self.write("spotbugsXml.xml", content)
                self.assertTrue(evaluate(self.target)[0])

    def test_pmd_and_cpd_analysis_errors_fail(self):
        for filename, content in (("pmd.xml", '<pmd><configerror rule="example"/></pmd>'), ("pmd.xml", '<pmd><error/></pmd>'), ("cpd.xml", '<pmd-cpd><error/></pmd-cpd>')):
            with self.subTest(filename=filename, content=content):
                self.write(filename, content)
                self.assertTrue(evaluate(self.target)[0])
                self.write(filename, '<pmd/>' if filename == 'pmd.xml' else '<pmd-cpd/>')


if __name__ == "__main__":
    unittest.main()
