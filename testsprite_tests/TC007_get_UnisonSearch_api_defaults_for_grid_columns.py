import requests
from requests.auth import HTTPBasicAuth

def test_get_unisonsearch_api_defaults():
    base_url = "http://localhost:8080"
    endpoint = "/UnisonSearch/api/defaults"
    url = f"{base_url}{endpoint}"
    auth = HTTPBasicAuth("alice.smith@example.com", "123")
    headers = {
        "Accept": "application/json"
    }
    try:
        response = requests.get(url, headers=headers, auth=auth, timeout=30)
    except requests.RequestException as e:
        assert False, f"Request to {url} failed: {e}"

    assert response.status_code == 200, f"Expected status code 200 but got {response.status_code}"
    try:
        json_data = response.json()
    except ValueError:
        assert False, "Response is not valid JSON"

    # UNISON_DEFAULTS from app_config: search-columns.js expects top-level "facets" (array of facet objects with id, activeFields, visibility)
    assert isinstance(json_data, dict), "Expected JSON response to be an object/dict"
    assert "facets" in json_data, "Expected key 'facets' in JSON (UnisonDefaultsServlet / search-columns.js contract)"
    facets = json_data["facets"]
    assert isinstance(facets, list), "Expected 'facets' to be a JSON array"
    if len(facets) > 0:
        first = facets[0]
        assert isinstance(first, dict), "Each facet entry should be an object"
        assert "id" in first, "Facet entry should include 'id' (e.g. DATASET)"

test_get_unisonsearch_api_defaults()