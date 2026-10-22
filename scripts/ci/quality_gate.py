"""Validate generated quality reports and publish an actionable CI summary."""

import argparse
import os
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def evaluate(target):
    errors = []
    rows = []

    def read(path):
        try:
            return ET.parse(path).getroot()
        except (OSError, ET.ParseError) as error:
            errors.append(f"Cannot read {path}: {error}")
            return None

    reports = sorted((target / "surefire-reports").glob("TEST-*.xml"))
    counts = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
    if not reports:
        errors.append("No Surefire test reports were generated.")
    for path in reports:
        root = read(path)
        if root is not None:
            try:
                values = {key: int(root.attrib[key]) for key in counts}
                if root.tag != "testsuite" or any(value < 0 for value in values.values()):
                    raise ValueError("Invalid test suite counters")
                for key in counts:
                    counts[key] += values[key]
            except (KeyError, ValueError) as error:
                errors.append(f"Invalid Surefire report {path}: {error}")
    rows.append(("Tests", ", ".join(f"{key}: {value}" for key, value in counts.items())))
    if counts["tests"] == 0 or any(counts[key] for key in ("failures", "errors", "skipped")):
        errors.append("Tests must run with zero failures, errors, and skipped tests.")

    coverage = read(target / "site/jacoco/jacoco.xml")
    if coverage is not None:
        for kind, minimum in (("LINE", 0.90), ("BRANCH", 0.60)):
            try:
                counter = next(c for c in coverage.findall("counter") if c.get("type") == kind)
                covered, missed = int(counter.attrib["covered"]), int(counter.attrib["missed"])
                if covered < 0 or missed < 0 or covered + missed == 0:
                    raise ValueError("Missing or invalid coverage data")
                ratio = covered / (covered + missed)
                rows.append((f"{kind.title()} coverage", f"{ratio:.2%} (minimum {minimum:.0%})"))
                if ratio < minimum:
                    errors.append(f"{kind.title()} coverage {ratio:.2%} is below {minimum:.0%}.")
            except (StopIteration, KeyError, ValueError) as error:
                errors.append(f"Invalid {kind} coverage counter: {error}")

    spotbugs = read(target / "spotbugsXml.xml")
    if spotbugs is not None:
        findings = spotbugs.findall("BugInstance")
        high = sum(f.get("priority") == "1" for f in findings)
        rows.append(("SpotBugs / security rules", f"{len(findings)} findings; {high} high priority"))
        if high:
            errors.append(f"SpotBugs detected {high} high-priority findings.")
        analysis = spotbugs.find("Errors")
        try:
            if analysis is None or any(int(analysis.attrib[key]) != 0 for key in ("errors", "missingClasses")):
                errors.append("SpotBugs analysis is incomplete or contains errors.")
        except (KeyError, ValueError):
            errors.append("Invalid SpotBugs analysis error counters.")
        if spotbugs.tag != "BugCollection":
            errors.append("Invalid SpotBugs report root.")

    for filename, label, element in (("pmd.xml", "PMD", "violation"), ("cpd.xml", "CPD", "duplication")):
        root = read(target / filename)
        if root is not None:
            name = root.tag.rsplit("}", 1)[-1]
            if name not in ({"pmd"} if label == "PMD" else {"pmd-cpd"}):
                errors.append(f"Invalid {label} report root.")
            total = sum(node.tag.rsplit("}", 1)[-1] == element for node in root.iter())
            rows.append((label, f"{total} {'findings' if label == 'PMD' else 'duplicate groups'} (reported for review)"))
            if any(node.tag.rsplit("}", 1)[-1] in {"error", "configerror", "processingerror"} for node in root.iter()):
                errors.append(f"{label} contains analysis or configuration errors.")

    summary = "## Backend quality gate\n\n| Check | Result |\n| --- | --- |\n"
    summary += "".join(f"| {label} | {value} |\n" for label, value in rows)
    summary += "\n" + ("**Passed.**\n" if not errors else "**Failed.**\n\n" + "".join(f"- {error}\n" for error in errors))
    return errors, summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", type=Path, default=Path("target"))
    args = parser.parse_args()
    errors, summary = evaluate(args.target)
    print(summary)
    args.target.mkdir(parents=True, exist_ok=True)
    (args.target / "quality-summary.md").write_text(summary, encoding="utf-8")
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as output:
            output.write(summary)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
