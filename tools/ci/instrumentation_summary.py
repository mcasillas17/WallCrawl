"""Prints the test total and the slowest classes and tests from AGP's connected-test JUnit XML."""
import glob
import sys
import xml.etree.ElementTree as ET

paths = sys.argv[1:] or glob.glob("app/build/outputs/androidTest-results/connected/debug/TEST-*.xml")
suites, cases = [], []
for path in paths:
    for suite in ET.parse(path).getroot().iter("testsuite"):
        suites.append((float(suite.get("time", 0)), int(suite.get("tests", 0)), suite.get("name")))
        for case in suite.iter("testcase"):
            cases.append((float(case.get("time", 0)), f'{case.get("classname", "").rsplit(".", 1)[-1]}.{case.get("name")}'))

print(f"{sum(s[1] for s in suites)} tests in {len(suites)} classes, {sum(s[0] for s in suites):.0f}s of test time")
print("Slowest classes:")
for time, tests, name in sorted(suites, reverse=True)[:15]:
    print(f"  {time:7.1f}s  {tests:3d} tests  {name.rsplit('.', 1)[-1]}")
print("Slowest tests:")
for time, name in sorted(cases, reverse=True)[:15]:
    print(f"  {time:7.1f}s  {name}")
