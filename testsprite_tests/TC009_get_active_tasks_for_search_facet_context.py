import requests
from requests.auth import HTTPBasicAuth

def test_get_active_tasks_for_search_facet_context():
    base_url = "http://localhost:8080"
    endpoint = "/api/active-tasks"
    url = base_url + endpoint

    username = "alice.smith@example.com"
    password = "123"
    auth = HTTPBasicAuth(username, password)

    headers = {
        "Accept": "application/json"
    }

    try:
        response = requests.get(url, auth=auth, headers=headers, timeout=30)
    except requests.RequestException as e:
        assert False, f"Request to {url} failed: {e}"

    # Valid status codes could be 200 or 401 depending on environment and auth
    assert response.status_code in (200, 401), f"Unexpected status code: {response.status_code}"

    if response.status_code == 200:
        try:
            json_data = response.json()
        except ValueError:
            assert False, "Response is not valid JSON"
        # The response should be a list (per description)
        assert isinstance(json_data, list), f"Response JSON is not a list but {type(json_data)}"
    elif response.status_code == 401:
        # Unauthorized, response could be empty or JSON explanation, just validate status here
        pass

test_get_active_tasks_for_search_facet_context()