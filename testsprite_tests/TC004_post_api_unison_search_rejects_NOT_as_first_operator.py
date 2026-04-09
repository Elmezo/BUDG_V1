import requests
from requests.auth import HTTPBasicAuth

def test_post_api_unison_search_rejects_not_as_first_operator():
    base_url = "http://localhost:8080"
    endpoint = "/api/unison/search"
    url = base_url + endpoint
    auth = HTTPBasicAuth("alice.smith@example.com", "123")
    headers = {"Content-Type": "application/json"}
    payload = {
        "searches": [
            {
                "operator": "NOT",
                "facetId": "DATASET",
                "query": "example"
            }
        ],
        "filters": [],
        "options": {
            "maxDepth": 1,
            "includeCounts": True
        }
    }
    timeout = 30

    try:
        response = requests.post(url, json=payload, headers=headers, auth=auth, timeout=timeout)
    except requests.RequestException as e:
        assert False, f"Request failed: {e}"

    # Acceptable responses:
    # Either 400 Bad Request or 200 with JSON success=false indicating logical error
    if response.status_code == 400:
        # Expect 400 Bad Request error with appropriate message
        try:
            resp_json = response.json()
        except ValueError:
            resp_json = None
        # If JSON body present, check success is false or error message
        if resp_json:
            assert ("success" in resp_json and resp_json["success"] is False) or ("error" in resp_json), \
                f"400 response JSON should indicate failure or error, got: {resp_json}"
        else:
            # No JSON body but 400 is acceptable
            pass
    elif response.status_code == 200:
        # Expect JSON with success false and error info
        try:
            resp_json = response.json()
        except ValueError:
            assert False, "200 response missing JSON body"
        assert "success" in resp_json and resp_json["success"] is False, \
            f"Expected success:false in response JSON but got: {resp_json}"
    else:
        assert False, f"Unexpected status code {response.status_code} received. Response text: {response.text}"

test_post_api_unison_search_rejects_not_as_first_operator()