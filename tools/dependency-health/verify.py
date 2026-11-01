#!/usr/bin/env python3
"""Qualify scoped tooling constraints; write evidence without submitting a graph."""

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile


def run(command, log):
    with log.open("w") as output:
        result = subprocess.run(command, stdout=output, stderr=subprocess.STDOUT, timeout=600)
    if result.returncode:
        raise RuntimeError(f"{command[0]} failed ({result.returncode}); see {log}")
    print(f"PASS {log.name}", flush=True)


def main():
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python3 tools/dependency-health/verify.py OUTPUT_DIRECTORY")
    source = Path(__file__).resolve().parent
    root = source.parents[1]
    output = Path(sys.argv[1]).resolve()
    output.mkdir(parents=True, exist_ok=True)
    # Each run retains its own evidence and never replaces an earlier run.
    output = Path(tempfile.mkdtemp(prefix="qualification-", dir=output))
    print(f"Evidence: {output}", flush=True)
    os.chdir(root)
    java = Path(os.environ["JAVA_HOME"]) / "bin"
    gradle = [str(root / "gradlew"), "-I", str(source / "inventory.gradle"),
              f"-Dhealth.output={output}", "dependencyHealthInventory",
              "--no-daemon", "--console=plain"]
    run(gradle, output / "inventory.log")
    classpath = (output / "_classpath.classpath").read_text()
    classes = output / "probes"
    classes.mkdir()
    run([str(java / "javac"), "-sourcepath", "", "-cp", classpath, "-d", str(classes),
         *map(str, sorted(source.glob("*Probe.java")))], output / "compile-probes.log")
    run([*gradle, f"-Dhealth.probes={classes}"], output / "agp-probes.log")

    lint = (output / "_appandroidLintTool.classpath").read_text()
    run([str(java / "java"), "-Xmx128m", "-cp", str(classes) + os.pathsep + lint,
         "CryptoProbe", "fixed"], output / "lint-CryptoProbe.log")

    artifacts = json.loads((output / "guava-artifacts.json").read_text())
    required = {"::classpath", ":app:androidLintTool",
                ":app:kspDebugKotlinProcessorClasspath", ":app:kspReleaseKotlinProcessorClasspath",
                ":app:debugUnitTestRuntimeClasspath", ":app:debugAndroidTestRuntimeClasspath"}
    if not required.issubset(artifacts):
        raise AssertionError(f"Missing Guava classpaths: {required - artifacts.keys()}")
    for config, jars in artifacts.items():
        variant = "android" if "Test" in config else "jre"
        if len(jars) != 1 or not Path(jars[0]).name.endswith(f"-{variant}.jar"):
            raise AssertionError(f"Unexpected Guava variant on {config}: {jars}")
    collections = ["CompactHashMap", "CompactHashSet", "CompactLinkedHashMap",
                   "CompactLinkedHashSet", "MapMakerInternalMap"]
    for jar in sorted({jar for jars in artifacts.values() for jar in jars}):
        for collection in collections:
            run([str(java / "java"), "-Xmx64m", "-cp", str(classes) + os.pathsep + jar,
                 "GuavaProbe", collection, "fixed"], output / f"{Path(jar).stem}-{collection}.log")

    engines = json.loads((output / "ksp-engines.json").read_text())
    if set(engines) != {":app:kspDebugKotlin", ":app:kspReleaseKotlin"}:
        raise AssertionError(f"Missing KSP engine inventory: {engines}")
    for jar in sorted({engine["artifact"] for engine in engines.values()}):
        for collection in collections:
            # Diagnostic only: replacing the relocated copy requires a supported KSP release.
            run([str(java / "java"), "-Xmx64m", "-cp", str(classes) + os.pathsep + jar,
                 "GuavaProbe", collection, "inspect", "ksp"],
                output / f"{Path(jar).stem}-{collection}-residual.log")

    configs = json.loads((output / "configurations.json").read_text())
    for variant in ["debug", "release"]:
        for scope in ["CompileClasspath", "RuntimeClasspath"]:
            if any(item.startswith(("com.google.guava:guava:", "org.bouncycastle:",
                                    "org.bitbucket.b_c:", "org.jdom:"))
                   for item in configs[f":app:{variant}{scope}"]):
                raise AssertionError(f"Tool library entered shipped {variant}{scope}")
    print("PASS scoped constraints qualified; JDOM, Gradle Guava and KSP's shaded Guava remain residuals.")


if __name__ == "__main__":
    main()
