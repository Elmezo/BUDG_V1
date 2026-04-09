import requests
from requests.auth import HTTPBasicAuth

def test_get_filter_fields_for_dataset():
    base_url = "http://localhost:8080"
    endpoint = "/UnisonSearch/api/filter-fields"
    url = f"{base_url}{endpoint}"
    params = {"facetId": "DATASET"}
    auth = HTTPBasicAuth("alice.smith@example.com", "123")
    timeout = 30

    try:
        response = requests.get(url, params=params, auth=auth, timeout=timeout)
    except requests.RequestException as e:
        assert False, f"Request failed: {e}"

    assert response.status_code == 200, f"Expected HTTP 200, got {response.status_code}"

    content_type = response.headers.get("Content-Type", "")
    assert "application/json" in content_type, f"Expected JSON response, got Content-Type: {content_type}"

    try:
        data = response.json()
    except ValueError:
        assert False, "Response is not valid JSON"

    assert isinstance(data, (list, dict)), "Response JSON is not an array or object"

test_get_filter_fields_for_dataset()