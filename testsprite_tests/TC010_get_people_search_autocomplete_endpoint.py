import requests
from requests.auth import HTTPBasicAuth

def test_get_people_search_autocomplete():
    base_url = "http://localhost:8080"
    endpoint = "/UnisonSearch/api/people/search"
    params = {"query": "a", "limit": 5}
    auth = HTTPBasicAuth("alice.smith@example.com", "123")
    headers = {
        "Accept": "application/json"
    }
    timeout = 30

    try:
        response = requests.get(
            url=base_url + endpoint,
            params=params,
            headers=headers,
            auth=auth,
            timeout=timeout
        )
    except requests.RequestException as e:
        assert False, f"Request failed: {e}"

    # Assert status code 200, no 500 errors
    assert response.status_code == 200, f"Expected status code 200 but got {response.status_code}"

    # Assert response content type is JSON
    content_type = response.headers.get("Content-Type", "")
    assert "application/json" in content_type, f"Expected JSON response but got Content-Type: {content_type}"

    # Parse JSON and assert it is either list or object
    try:
        json_data = response.json()
    except ValueError:
        assert False, "Response is not valid JSON"

    # The response is expected to be JSON list (may be empty) or object
    # Validate type
    assert isinstance(json_data, (list, dict)), f"Expected JSON response to be a list or dict but got {type(json_data)}"

    # Ensure no internal server error indication
    # Typically 500 error will not reach here, but check response content to be safe
    json_dump_str = response.text.lower()
    assert "error" not in json_dump_str and "exception" not in json_dump_str and "stacktrace" not in json_dump_str, "Response contains error indication"

test_get_people_search_autocomplete()