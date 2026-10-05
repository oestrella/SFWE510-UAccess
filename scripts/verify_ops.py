#!/usr/bin/env python3
"""Explicit local Compose demonstrations. Run from the application repository root."""
import argparse
import json
from pathlib import Path
import subprocess
import time
import uuid
from smoke import request, wait_ready


def compose(*arguments):
    subprocess.run(["docker", "compose", *arguments], check=True, timeout=300)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--refresh", action="store_true")
    parser.add_argument("--outage", action="store_true")
    parser.add_argument("--persistence", action="store_true")
    parser.add_argument("--database-health", action="store_true")
    parser.add_argument("--evidence", default="tmp/smoke-evidence.json")
    args = parser.parse_args()
    assert any([args.refresh, args.outage, args.persistence, args.database_health]), "Choose at least one check."
    # Reuse the smoke IDs so these checks test continuity, not just creation of new records.
    evidence = json.loads(Path(args.evidence).read_text(encoding="utf-8"))
    course, enrollment = evidence["courseUrl"], evidence["enrollmentUrl"]
    course_path = course + "/api/v1/courses/" + evidence["courseId"]
    student_path = enrollment + "/api/v1/students/" + evidence["studentId"]
    enrollment_path = student_path + "/enrollments/" + evidence["enrollmentId"]
    ready = [url + "/actuator/health/readiness" for url in (course, enrollment, evidence["configUrl"])]
    wait_ready(ready)
    if args.refresh:
        for service, url in [("course-service", course), ("enrollment-service", enrollment)]:
            config_file = Path("config-repo") / (service + "-dev.yml")
            # Restore the exact file bytes after the refresh exercise, even if an assertion fails.
            original = config_file.read_bytes()
            before, _ = request("GET", url + "/api/v1/info")
            assert "dev" in before["profiles"], "Refresh demonstration requires dev."
            banner = before["banner"]
            replacement = banner + " (refreshed)"
            try:
                assert banner.encode() in original, "Banner must appear literally in the dev config."
                config_file.write_bytes(original.replace(banner.encode(), replacement.encode()))
                request("POST", url + "/actuator/refresh", 200, {})
                after, _ = request("GET", url + "/api/v1/info")
                assert after["banner"] == replacement, after
            finally:
                config_file.write_bytes(original)
                request("POST", url + "/actuator/refresh", 200, {})
            restored, _ = request("GET", url + "/api/v1/info")
            assert restored["banner"] == banner
            print(f"PASS: {service} live refresh and restore")
    if args.persistence:
        before_course, _ = request("GET", course_path)
        before_student, _ = request("GET", student_path)
        before_enrollment, _ = request("GET", enrollment_path)
        # Recreate all five containers, preserving their named database volumes.
        compose("up", "-d", "--no-build", "--force-recreate", "--wait", "--wait-timeout", "240")
        wait_ready(ready)
        after_course, _ = request("GET", course_path)
        after_student, _ = request("GET", student_path)
        after_enrollment, _ = request("GET", enrollment_path)
        assert before_course == after_course and before_student == after_student and before_enrollment == after_enrollment
        print("PASS: Course, Student, Enrollment, and timestamp survive recreation of all five containers")
    if args.outage:
        # Perform after persistence because the drop deliberately removes the saved Enrollment.
        compose("stop", "course-service")
        try:
            student, _ = request("GET", student_path)
            request("PUT", student_path, 200, {key: student[key] for key in ("studentNumber", "name", "email")})
            token = uuid.uuid4().hex[:10]
            request("POST", enrollment + "/api/v1/students", 201,
                    {"studentNumber": "OUTAGE" + token, "name": "Fictitious Outage Student",
                     "email": "outage" + token + "@example.invalid"})
            existing, _ = request("GET", student_path + "/enrollments")
            assert existing["totalElements"] == 1
            failed, _ = request("POST", student_path + "/enrollments", 503, {"courseId": evidence["courseId"]})
            assert failed["code"] == "COURSE_UNAVAILABLE"
            existing, _ = request("GET", student_path + "/enrollments")
            assert existing["totalElements"] == 1
            request("DELETE", enrollment_path, 204)
            empty, _ = request("GET", student_path + "/enrollments")
            assert empty["totalElements"] == 0
            request("GET", enrollment + "/actuator/health/readiness")
            print("PASS: Course outage returns 503; Student/list/drop and Enrollment readiness remain available")
        finally:
            compose("up", "-d", "--no-build", "--wait", "--wait-timeout", "240")
        # A restarted upstream can briefly return 503 to an existing caller while
        # connections/DNS recover. Retry only this known no-write failure, bounded.
        deadline = time.monotonic() + 60
        while True:
            joined, _ = request("POST", student_path + "/enrollments", (201, 503), {"courseId": evidence["courseId"]})
            if joined.get("status") != 503:
                break
            assert time.monotonic() < deadline, "Upstream did not recover within 60 seconds."
            time.sleep(0.5)
        assert joined["id"] != evidence["enrollmentId"]
        evidence["enrollmentId"] = joined["id"]
        Path(args.evidence).write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    # Readiness and liveness answer different questions when a backing database goes down.
    if args.database_health:
        compose("stop", "enrollment-db")
        try:
            down, _ = request("GET", enrollment + "/actuator/health/readiness", 503)
            live, _ = request("GET", enrollment + "/actuator/health/liveness")
            assert down["status"] == "DOWN" and live["status"] == "UP"
            request("GET", course_path)
            print("PASS: DB outage affects Enrollment readiness while liveness and Course remain available")
        finally:
            compose("up", "-d", "--no-build", "--wait", "--wait-timeout", "240")
        wait_ready(ready)


if __name__ == "__main__":
    main()
