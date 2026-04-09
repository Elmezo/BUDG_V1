import requests
from requests.auth import HTTPBasicAuth

def test_get_api_unison_search_returns_method_not_allowed():
    base_url = "http://localhost:8080"
    endpoint = "/api/unison/search"
    url = f"{base_url}{endpoint}"
    auth = HTTPBasicAuth("alice.smith@example.com", "123")
    headers = {
        "Accept": "application/json"
    }
    timeout = 30

    try:
        response = requests.get(url, headers=headers, auth=auth, timeout=timeout)
    except requests.RequestException as e:
        assert False, f"Request failed: {e}"

    # Assert status code 405 or documented not-allowed code
    # Commonly 405 for method not allowed
    assert response.status_code == 405, f"Expected status code 405 but got {response.status_code}"

    # Assert response Content-Type is application/json (or contains it)
    content_type = response.headers.get("Content-Type", "")
    assert "application/json" in content_type.lower(), f"Expected JSON content type but got {content_type}"

    try:
        json_body = response.json()
    except ValueError:
        assert False, "Response is not valid JSON"

    # Verify json body is present and is a dict (some structure expected)
    assert isinstance(json_body, dict), "Response JSON body is not an object"

    # Optionally check for presence of error or message keys indicating not allowed
    assert any(key in json_body for key in ["error", "message", "status", "detail"]), "JSON body does not contain expected error fields indicating method not allowed"

test_get_api_unison_search_returns_method_not_allowed()