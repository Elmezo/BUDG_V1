import requests
from requests.auth import HTTPBasicAuth

def test_post_api_unison_search_with_valid_find_query():
    base_url = "http://localhost:8080"
    endpoint = "/api/unison/search"
    url = base_url + endpoint
    auth = HTTPBasicAuth("alice.smith@example.com", "123")
    headers = {"Content-Type": "application/json"}
    payload = {
        "searches": [
            {
                "operator": "FIND",
                "facet": "DATASET",
                "keywords": "test",
                "filters": [],
                "options": {
                    "maxDepth": 1,
                    "includeCounts": True
                }
            }
        ]
    }

    try:
        response = requests.post(url, json=payload, headers=headers, auth=auth, timeout=30)
        assert response.status_code == 200, f"Expected status 200, got {response.status_code}"
        json_resp = response.json()
        assert "success" in json_resp, "Response missing 'success' key"
        assert json_resp["success"] is True, "'success' is not True"
        assert "results" in json_resp, "Response missing 'results' key"
    except requests.RequestException as e:
        assert False, f"Request failed: {e}"

test_post_api_unison_search_with_valid_find_query()