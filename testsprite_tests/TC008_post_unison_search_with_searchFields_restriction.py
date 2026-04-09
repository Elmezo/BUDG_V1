import requests
from requests.auth import HTTPBasicAuth

def test_post_unison_search_with_searchfields_restriction():
    base_url = "http://localhost:8080"
    endpoint = "/api/unison/search"
    url = base_url + endpoint

    auth = HTTPBasicAuth("alice.smith@example.com", "123")
    headers = {
        "Content-Type": "application/json"
    }
    payload = {
        "searches": [
            {
                "operator": "FIND",
                "searchFields": {
                    "name": True,
                    "ref": False
                },
                "keyword": "test"
            }
        ],
        "filters": {},
        "options": {
            "maxDepth": 1,
            "includeCounts": True
        }
    }

    try:
        response = requests.post(url, json=payload, headers=headers, auth=auth, timeout=30)
        assert response.status_code == 200, f"Expected HTTP 200 but got {response.status_code}"
        resp_json = response.json()
        assert resp_json.get("success") is True, f"Expected success True but got {resp_json.get('success')}"

        # Validate response structure consistent with FacetResult
        # FacetResult likely includes keys like: 'results', 'facets', 'counts' (based on typical search APIs)
        # Let's assert some keys presence as per typical FacetResult shape
        assert "results" in resp_json, "Response missing 'results' field"
        # results should be list or dict (depending on implementation)
        assert isinstance(resp_json["results"], (list, dict)), "'results' field is not list or dict"
        # Optional: facets or counts presence
        if "facets" in resp_json:
            assert isinstance(resp_json["facets"], (list, dict)), "'facets' field exists but is not list or dict"
        if "counts" in resp_json:
            assert isinstance(resp_json["counts"], (dict)), "'counts' field exists but is not dict"

    except requests.exceptions.RequestException as e:
        assert False, f"Request failed: {e}"

test_post_unison_search_with_searchfields_restriction()