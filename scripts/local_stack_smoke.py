"""Smoke tests for the locally running Slotix frontend and backend."""

import json
import os
import unittest
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


FRONTEND_URL = os.environ.get("SLOTIX_FRONTEND_URL", "http://127.0.0.1:4200").rstrip("/")
API_URL = os.environ.get("SLOTIX_API_URL", "http://127.0.0.1:8080").rstrip("/")
TIMEOUT_SECONDS = 5


def assert_json_content_type(test_case, content_type):
    test_case.assertTrue(
        "application/json" in content_type or content_type.endswith("+json"),
        f"Expected a JSON content type, got {content_type!r}",
    )


def get_response(url):
    with urlopen(url, timeout=TIMEOUT_SECONDS) as response:
        return response.status, response.headers.get("Content-Type", ""), response.read()


class LocalStackSmokeTest(unittest.TestCase):
    def test_backend_readiness_is_up(self):
        status, content_type, body = get_response(f"{API_URL}/actuator/health/readiness")

        self.assertEqual(status, 200)
        assert_json_content_type(self, content_type)
        self.assertEqual(json.loads(body)["status"], "UP")

    def test_backend_openapi_document_is_available(self):
        status, content_type, body = get_response(f"{API_URL}/v3/api-docs")

        self.assertEqual(status, 200)
        assert_json_content_type(self, content_type)
        document = json.loads(body)
        self.assertIn("openapi", document)
        self.assertIn("paths", document)

    def test_frontend_serves_angular_application(self):
        status, content_type, body = get_response(f"{FRONTEND_URL}/")

        self.assertEqual(status, 200)
        self.assertIn("text/html", content_type)
        self.assertIn(b"<app-root", body)

    def test_frontend_proxy_forwards_login_to_backend(self):
        request = Request(
            f"{FRONTEND_URL}/api/users/login",
            data=json.dumps(
                {
                    "companyId": "00000000-0000-0000-0000-000000000000",
                    "email": "smoke-test@example.invalid",
                    "password": "intentionally-invalid",
                }
            ).encode(),
            headers={"Content-Type": "application/json"},
            method="POST",
        )

        try:
            with urlopen(request, timeout=TIMEOUT_SECONDS) as response:
                status, body = response.status, response.read()
        except HTTPError as error:
            status, body = error.code, error.read()
        except URLError as error:
            self.fail(f"Could not reach the frontend proxy: {error.reason}")

        self.assertEqual(status, 401, body.decode(errors="replace"))
        self.assertEqual(json.loads(body).get("code"), "INVALID_CREDENTIALS")


if __name__ == "__main__":
    unittest.main(verbosity=2)
