import requests
from requests.auth import HTTPBasicAuth

def test_get_unisonsearch_filter_fields_requires_facetId():
    base_url = "http://localhost:8080"
    endpoint = "/UnisonSearch/api/filter-fields"
    url = base_url + endpoint
    auth = HTTPBasicAuth("alice.smith@example.com", "123")
    headers = {
        "Accept": "application/json"
    }
    try:
        response = requests.get(url, headers=headers, auth=auth, timeout=30)
    except requests.RequestException as e:
        assert False, f"Request failed with exception: {e}"

    # Expecting 400 Bad Request because facetId query param is missing
    assert response.status_code == 400, f"Expected status code 400, got {response.status_code}"

    # Content-Type should be application/json
    content_type = response.headers.get("Content-Type", "")
    assert "application/json" in content_type.lower(), f"Expected JSON response, got Content-Type: {content_type}"

    # The response body should contain an error indication (JSON with error message)
    try:
        json_data = response.json()
    except ValueError:
        assert False, "Response is not valid JSON"

    # Response should contain keys indicating error, e.g. error or message
    assert isinstance(json_data, dict), "Expected response JSON to be a dictionary"
    assert ("error" in json_data or "message" in json_data), "Response JSON should contain 'error' or 'message' key"

test_get_unisonsearch_filter_fields_requires_facetId()