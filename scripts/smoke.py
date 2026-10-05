#!/usr/bin/env python3

# Exercise the real REST contract. Python standard library only; failures exit nonzero

import argparse
import concurrent.futures
import datetime
import json
from pathlib import Path
import time
import urllib.error
import urllib.request
import uuid

def request(method, url, expected=200, body=None, language=None):
    headers = {"Accept": "application/json"}
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode()
    if language:
        headers["Accept-Language"] = language
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        response = urllib.request.urlopen(req, timeout=10)
    except urllib.error.HTTPError as error:
        # Expected rejection responses are test results too, so inspect their status and body.
        response = error
    with response:
        status = response.code
        payload = response.read()
        result = json.loads(payload) if payload else None
        allowed = expected if isinstance(expected, tuple) else (expected,)
        assert status in allowed, f"{method} {url}: expected {expected}, got {status}: {result}"
        if status >= 400 and "/actuator/" not in url:
            assert result["status"] == status and result["code"] and result["detail"]
            assert result["instance"].startswith("/"), result
        return result, response.headers


def wait_ready(urls, timeout=120):
    # Poll actual readiness with a deadline instead of guessing how long startup takes.
    deadline = time.monotonic() + timeout
    pending = set(urls)
    while pending and time.monotonic() < deadline:
        for url in list(pending):
            try:
                health, _ = request("GET", url)
                if health["status"] == "UP":
                    pending.remove(url)
            except (OSError, AssertionError, ValueError):
                pass
        if pending:
            time.sleep(0.5)
    assert not pending, f"Readiness timeout after {timeout}s: {sorted(pending)}"


def smoke(course, enrollment, config, timeout):
    wait_ready([u + "/actuator/health/readiness" for u in (course, enrollment, config)], timeout)
    for url, name in [(course, "course-service"), (enrollment, "enrollment-service")]:
        info, _ = request("GET", url + "/api/v1/info")
        assert info["service"] == name and info["banner"] and info["profiles"]
    # Unique fictitious identities let me repeat the workflow without resetting the databases.
    token = uuid.uuid4().hex[:10].upper()
    courses = course + "/api/v1/courses"
    students = enrollment + "/api/v1/students"
    course_body = {"courseCode": " smoke" + token + " ", "title": "Fictitious smoke course", "credits": 3}
    created, headers = request("POST", courses, 201, course_body)
    course_id = created["id"]
    uuid.UUID(course_id)
    assert headers["Location"].endswith(course_id) and created["active"] is True
    assert created["courseCode"] == "SMOKE" + token
    request("POST", courses, 409, course_body)
    request("POST", courses, 400, dict(course_body, credits=9))
    updated_course = dict(course_body, title="Updated smoke course", description="Demo only", active=True)
    request("PUT", courses + "/" + course_id, 200, updated_course)
    read_course, _ = request("GET", courses + "/" + course_id)
    assert read_course["title"] == "Updated smoke course"
    assert read_course["_links"]["self"]["href"].endswith(course_id)
    catalog, _ = request("GET", courses + "?search=SMOKE" + token)
    assert catalog["totalElements"] == 1 and catalog["content"][0]["id"] == course_id
    request("GET", courses + "?size=101", 400)
    request("GET", courses + "/not-a-uuid", 400)
    missing_id = str(uuid.uuid4())
    spanish, _ = request("GET", courses + "/" + missing_id, 404, language="es")
    english, _ = request("GET", courses + "/" + missing_id, 404, language="fr")
    assert spanish["code"] == english["code"] == "COURSE_NOT_FOUND"
    assert spanish["detail"] == "No se encontro el curso."
    assert english["detail"] == "Course was not found."
    created_students = []
    for number in (1, 2):
        body = {"studentNumber": f" smoke{token}{number} ", "name": f"Fictitious Student {number}",
                "email": f" SMOKE{token}{number}@example.invalid "}
        student, headers = request("POST", students, 201, body)
        uuid.UUID(student["id"])
        assert student["email"] == body["email"].strip().lower()
        assert headers["Location"].endswith(student["id"])
        created_students.append(student)
    first, second = created_students
    request("POST", students, 400, {"studentNumber": "BAD" + token, "name": "Demo", "email": "invalid"})
    request("PUT", students + "/" + first["id"], 200,
            {"studentNumber": first["studentNumber"], "name": "Updated Demo Student", "email": first["email"]})
    student_read, _ = request("GET", students + "/" + first["id"])
    assert student_read["name"] == "Updated Demo Student" and "enrollments" in student_read["_links"]
    path = students + "/" + first["id"] + "/enrollments"
    wrong_path = students + "/" + second["id"] + "/enrollments"
    registration = {"courseId": course_id}
    joined, headers = request("POST", path, 201, registration)
    old_id = joined["id"]
    assert joined["courseId"] == course_id and headers["Location"].endswith(old_id)
    assert datetime.datetime.fromisoformat(joined["enrolledAt"].replace("Z", "+00:00")).utcoffset().total_seconds() == 0
    duplicate, _ = request("POST", path, 409, registration)
    assert duplicate["code"] == "ALREADY_ENROLLED"
    request("GET", wrong_path + "/" + old_id, 404)
    request("DELETE", wrong_path + "/" + old_id, 404)
    request("GET", path + "/" + old_id)
    request("POST", path, 404, {"courseId": str(uuid.uuid4())})
    request("POST", path, 400, {"courseId": "invalid"})
    request("GET", students + "/" + str(uuid.uuid4()) + "/enrollments", 404)
    # Repeating archive checks idempotence; existing enrollment data should still be there.
    for _ in range(2):
        archived, _ = request("PATCH", courses + "/" + course_id + "/status", 200, {"active": False})
        assert archived["active"] is False
    inactive, _ = request("POST", wrong_path, 409, registration)
    assert inactive["code"] == "COURSE_INACTIVE"
    current, _ = request("GET", path)
    assert current["totalElements"] == 1
    catalog, _ = request("GET", courses + "?search=SMOKE" + token)
    assert catalog["totalElements"] == 0
    catalog, _ = request("GET", courses + "?active=false&search=SMOKE" + token)
    assert catalog["totalElements"] == 1
    request("DELETE", path + "/" + old_id, 204)
    request("DELETE", path + "/" + old_id, 404)
    request("PUT", courses + "/" + course_id, 200, updated_course)
    joined, _ = request("POST", path, 201, registration)
    assert joined["id"] != old_id
    # Also demonstrate concurrent registration against the complete Compose stack.
    def race():
        req = urllib.request.Request(wrong_path, data=json.dumps(registration).encode(),
                                     headers={"Content-Type": "application/json"}, method="POST")
        try:
            response = urllib.request.urlopen(req, timeout=10)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            return response.code
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as workers:
        results = list(workers.map(lambda _: race(), range(2)))
    assert sorted(results) == [201, 409], results
    other_list, _ = request("GET", wrong_path)
    assert other_list["totalElements"] == 1
    print("PASS: create/update, validation, normalization, hypermedia, localization, enrollment, archive, scoped drop, re-enrollment, concurrency")
    return {"courseId": course_id, "studentId": first["id"], "secondStudentId": second["id"],
            "enrollmentId": joined["id"], "courseUrl": course, "enrollmentUrl": enrollment, "configUrl": config}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--course-url", default="http://127.0.0.1:8081")
    parser.add_argument("--enrollment-url", default="http://127.0.0.1:8082")
    parser.add_argument("--config-url", default="http://127.0.0.1:8071")
    parser.add_argument("--wait-timeout", type=float, default=120)
    parser.add_argument("--save", default="tmp/smoke-evidence.json")
    args = parser.parse_args()
    evidence = smoke(args.course_url.rstrip("/"), args.enrollment_url.rstrip("/"),
                     args.config_url.rstrip("/"), args.wait_timeout)
    # Save the returned UUIDs so the later persistence checks can compare the same records.
    destination = Path(args.save)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    print(f"Saved generated IDs for operational checks: {destination}")


if __name__ == "__main__":
    main()
