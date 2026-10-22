"""Fail when Trivy did not actually scan the resolved Android dependencies."""
import json
import sys
from pathlib import Path

report = json.loads(Path(sys.argv[1]).read_text())
results = [r for r in report.get("Results", []) if r.get("Type") == "gradle"]
packages = [p for r in results for p in r.get("Packages", [])]
if not packages:
    raise SystemExit("Trivy did not report Gradle packages; refusing an empty dependency scan")
# Check every resolved coordinate, not just a non-empty result from an unrelated file.
lock = Path(".trivy-dependencies/gradle.lockfile").read_text()
expected = {line.split("=", 1)[0] for line in lock.splitlines()
            if line and not line.startswith("#") and "=" in line and line.count(":") >= 2}
actual = {f"{p['Name']}:{p['Version']}" for p in packages}
missing = expected - actual
if not expected or missing:
    raise SystemExit(f"Incomplete Gradle scan: {len(missing)} of {len(expected)} coordinates missing")
print(f"Trivy analyzed all {len(expected)} resolved Gradle coordinates")
