#!/usr/bin/env python3
"""Verify executable JARs with host JDK 21 and Compose PostgreSQL; restore Compose afterward."""
import contextlib
import os
from pathlib import Path
import subprocess
from smoke import smoke, wait_ready


def compose(*args):
    subprocess.run(["docker", "compose", *args], check=True, timeout=300)


def main():
    java_home = os.environ.get("JAVA_HOME")
    assert java_home, "Set JAVA_HOME to JDK 21 first."
    java = str(Path(java_home) / "bin" / ("java.exe" if os.name == "nt" else "java"))
    version = subprocess.check_output([java, "--version"], text=True)
    assert 'version "21.' in version or "openjdk 21." in version, version
    settings = {}
    for line in Path(".env").read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.lstrip().startswith("#"):
            name, value = line.split("=", 1)
            settings[name.strip()] = value.strip().strip('"').strip("'")
    # Explicitly read .env here; a host Java process does not do this automatically.
    clean = {k: v for k, v in os.environ.items() if not k.startswith(("COURSE_DB_", "ENROLLMENT_DB_"))
             and k not in {"SPRING_PROFILES_ACTIVE", "CONFIG_SERVER_URL", "CONFIG_REPO_LOCATION", "COURSE_API_URL", "DEMO_ENABLED"}}
    clean.update({"SPRING_PROFILES_ACTIVE": "dev", "DEMO_ENABLED": "true"})
    processes = []
    logs = Path("tmp")
    logs.mkdir(exist_ok=True)
    with contextlib.ExitStack() as stack:
        try:
            compose("stop", "course-service", "enrollment-service", "config-server")
            compose("up", "-d", "course-db", "enrollment-db", "--wait", "--wait-timeout", "120")
            for module, prefix, port in [("config-server", None, 8071), ("course-service", "COURSE", 8081),
                                         ("enrollment-service", "ENROLLMENT", 8082)]:
                env = dict(clean)
                # Give each business process only its own database credentials.
                if prefix:
                    env[prefix + "_DB_USER"] = settings[prefix + "_DB_USER"]
                    env[prefix + "_DB_PASSWORD"] = settings[prefix + "_DB_PASSWORD"]
                else:
                    env["SPRING_PROFILES_ACTIVE"] = "native"
                log = stack.enter_context((logs / ("host-" + module + ".log")).open("w", encoding="utf-8"))
                jar = Path(module) / "target" / (module + "-1.0.0-SNAPSHOT.jar")
                assert jar.is_file(), "Build the JARs with Maven first."
                process = subprocess.Popen([java, "-jar", str(jar)], env=env, stdout=log, stderr=subprocess.STDOUT,
                                           creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
                processes.append(process)
                # Start the next JAR only after this one can actually serve requests.
                wait_ready([f"http://127.0.0.1:{port}/actuator/health/readiness"], 120)
                assert process.poll() is None, f"{module} exited; inspect its host log."
            smoke("http://127.0.0.1:8081", "http://127.0.0.1:8082", "http://127.0.0.1:8071", 120)
            print("PASS: all three executable JARs run with host JDK 21, PostgreSQL, required Config Server, and full smoke workflow")
        finally:
            # Stop only the host processes created here and restore the normal Compose demo.
            for process in reversed(processes):
                process.terminate()
                try:
                    process.wait(timeout=20)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5)
            compose("up", "-d", "--no-build", "--wait", "--wait-timeout", "240")


if __name__ == "__main__":
    main()
