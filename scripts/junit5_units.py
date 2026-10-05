#!/usr/bin/env python3
"""Run the 20 business/controller unit tests with an isolated JUnit 5 runtime.

Boot 4's SpringExtension needs its managed JUnit 6 runtime for integration tests.
This separate process preserves the prompt's JUnit 5 unit-test demonstration.
"""
import os
from pathlib import Path
import subprocess
import urllib.request


def main():

    # JDK 21
    root = Path(__file__).resolve().parent.parent
    java_home = os.environ.get("JAVA_HOME")
    assert java_home, "Set JAVA_HOME to JDK 21."

    java = Path(java_home) / "bin" / ("java.exe" if os.name == "nt" else "java")
    wrapper = root / ("mvnw.cmd" if os.name == "nt" else "mvnw")
    console = root / "tmp" / "junit-platform-console-standalone-1.14.4.jar"
    console.parent.mkdir(exist_ok=True)

    if not console.exists():
        urllib.request.urlretrieve(
            "https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-console-standalone/1.14.4/"
            "junit-platform-console-standalone-1.14.4.jar", console)


    # I run only the business/controller unit tests here; Spring integration uses its managed runtime.
    for module in ("course-service", "enrollment-service"):

        output = root / module / "target" / "junit5-classpath.txt"
        subprocess.run([str(wrapper), "-B", "-ntp", "-pl", module, "dependency:build-classpath",
                        "-DincludeScope=test", "-Dmdep.outputFile=" + str(output)], cwd=root, check=True, timeout=180)

        # Remove the managed JUnit jars so this separate JVM has one consistent JUnit 5 runtime.
        dependencies = [p for p in output.read_text().strip().split(os.pathsep)
                        if "/org/junit/" not in p.replace("\\", "/")]

        dependencies += [str(root / module / "target" / "classes"),
                         str(root / module / "target" / "test-classes")]

        subprocess.run([str(java), "-jar", str(console), "execute", "--disable-ansi-colors",
                        "--class-path", os.pathsep.join(dependencies), "--scan-class-path",
                        "--include-classname=.*Test", "--fail-if-no-tests"], cwd=root, check=True, timeout=120)


if __name__ == "__main__":
    main()
