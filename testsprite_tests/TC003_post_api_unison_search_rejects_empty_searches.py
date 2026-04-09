import requests
from requests.auth import HTTPBasicAuth

def test_post_api_unison_search_rejects_empty_searches():
    base_url = "http://localhost:8080"
    endpoint = "/api/unison/search"
    url = base_url + endpoint

    auth = HTTPBasicAuth("alice.smith@example.com", "123")
    headers = {
        "Content-Type": "application/json"
    }
    timeout = 30

    # Test cases: searches: [] and missing searches
    test_payloads = [
        {"searches": []},
        {}
    ]

    for payload in test_payloads:
        try:
            response = requests.post(url, json=payload, headers=headers, auth=auth, timeout=timeout)
            # Validate HTTP status codes and JSON body conditions
            # Acceptable: HTTP 400 or JSON success false with error message
            if response.status_code == 400:
                # If 400, response body may be empty or contain error info, just assert status code here
                assert response.status_code == 400
            else:
                # Otherwise, expect a JSON response with success false and error message
                json_resp = response.json()
                assert "success" in json_resp, "Response JSON missing 'success' field"
                assert json_resp["success"] is False, "Expected success to be False"
                assert ("error" in json_resp and isinstance(json_resp["error"], str) and json_resp["error"].strip()), \
                    "Expected non-empty error message in response"
                # Status code should be 200 or 400, but test accepts non-400 with success false
                assert response.status_code in (200, 400)
        except requests.exceptions.RequestException as e:
            assert False, f"Request failed: {str(e)}"

test_post_api_unison_search_rejects_empty_searches()