
# TestSprite AI Testing Report(MCP)

---

## 1️⃣ Document Metadata
- **Project Name:** BUDG_V2
- **Date:** 2026-04-08
- **Prepared by:** TestSprite AI Team

---

## 2️⃣ Requirement Validation Summary

#### Test TC001 post api unison search with valid FIND query
- **Test Code:** [TC001_post_api_unison_search_with_valid_FIND_query.py](./TC001_post_api_unison_search_with_valid_FIND_query.py)
- **Test Visualization and Result:** https://www.testsprite.com/dashboard/mcp/tests/9e21f103-80ab-4614-a08d-b67ca8c3157a/5ef49b10-3675-4fd3-b41a-290fac78d1ab
- **Status:** ✅ Passed
- **Analysis / Findings:** {{TODO:AI_ANALYSIS}}.
---

#### Test TC002 get api unison search returns method not allowed
- **Test Code:** [TC002_get_api_unison_search_returns_method_not_allowed.py](./TC002_get_api_unison_search_returns_method_not_allowed.py)
- **Test Visualization and Result:** https://www.testsprite.com/dashboard/mcp/tests/9e21f103-80ab-4614-a08d-b67ca8c3157a/b2fa8e7b-503a-4098-ac83-0ab1c45303af
- **Status:** ✅ Passed
- **Analysis / Findings:** {{TODO:AI_ANALYSIS}}.
---

#### Test TC003 post api unison search rejects empty searches
- **Test Code:** [TC003_post_api_unison_search_rejects_empty_searches.py](./TC003_post_api_unison_search_rejects_empty_searches.py)
- **Test Visualization and Result:** https://www.testsprite.com/dashboard/mcp/tests/9e21f103-80ab-4614-a08d-b67ca8c3157a/e3eb06ac-313e-4a5e-bba8-248859b79900
- **Status:** ✅ Passed
- **Analysis / Findings:** {{TODO:AI_ANALYSIS}}.
---

#### Test TC004 post api unison search rejects NOT as first operator
- **Test Code:** [TC004_post_api_unison_search_rejects_NOT_as_first_operator.py](./TC004_post_api_unison_search_rejects_NOT_as_first_operator.py)
- **Test Visualization and Result:** https://www.testsprite.com/dashboard/mcp/tests/9e21f103-80ab-4614-a08d-b67ca8c3157a/8d213383-87e1-4604-a12e-43a84c03d0f5
- **Status:** ✅ Passed
- **Analysis / Findings:** {{TODO:AI_ANALYSIS}}.
---

#### Test TC005 get UnisonSearch filter-fields requires facetId
- **Test Code:** [TC005_get_UnisonSearch_filter_fields_requires_facetId.py](./TC005_get_UnisonSearch_filter_fields_requires_facetId.py)
- **Test Visualization and Result:** https://www.testsprite.com/dashboard/mcp/tests/9e21f103-80ab-4614-a08d-b67ca8c3157a/e74c2b2c-f767-40e2-abd8-91845c62c959
- **Status:** ✅ Passed
- **Analysis / Findings:** {{TODO:AI_ANALYSIS}}.
---

#### Test TC006 get UnisonSearch filter-fields for DATASET returns metadata
- **Test Code:** [TC006_get_UnisonSearch_filter_fields_for_DATASET_returns_metadata.py](./TC006_get_UnisonSearch_filter_fields_for_DATASET_returns_metadata.py)
- **Test Visualization and Result:** https://www.testsprite.com/dashboard/mcp/tests/9e21f103-80ab-4614-a08d-b67ca8c3157a/8cbb2139-2b77-44de-b9fa-e6c93c826414
- **Status:** ✅ Passed
- **Analysis / Findings:** {{TODO:AI_ANALYSIS}}.
---

#### Test TC007 get UnisonSearch api defaults for grid columns
- **Test Code:** [TC007_get_UnisonSearch_api_defaults_for_grid_columns.py](./TC007_get_UnisonSearch_api_defaults_for_grid_columns.py)
- **Test Error:** Traceback (most recent call last):
  File "/var/task/handler.py", line 258, in run_with_retry
    exec(code, exec_env)
  File "<string>", line 32, in <module>
  File "<string>", line 30, in test_get_unisonsearch_api_defaults
AssertionError: Expected key 'columns' in JSON response

- **Test Visualization and Result:** https://www.testsprite.com/dashboard/mcp/tests/9e21f103-80ab-4614-a08d-b67ca8c3157a/1307369e-481b-4d2d-a693-d2fe528f7452
- **Status:** ❌ Failed
- **Analysis / Findings:** {{TODO:AI_ANALYSIS}}.
---

#### Test TC008 post unison search with searchFields restriction
- **Test Code:** [TC008_post_unison_search_with_searchFields_restriction.py](./TC008_post_unison_search_with_searchFields_restriction.py)
- **Test Visualization and Result:** https://www.testsprite.com/dashboard/mcp/tests/9e21f103-80ab-4614-a08d-b67ca8c3157a/14b9d842-c63b-4ef9-94f3-361d5fe5aa01
- **Status:** ✅ Passed
- **Analysis / Findings:** {{TODO:AI_ANALYSIS}}.
---

#### Test TC009 get active tasks for search facet context
- **Test Code:** [TC009_get_active_tasks_for_search_facet_context.py](./TC009_get_active_tasks_for_search_facet_context.py)
- **Test Visualization and Result:** https://www.testsprite.com/dashboard/mcp/tests/9e21f103-80ab-4614-a08d-b67ca8c3157a/b64089b4-88eb-4103-b890-79a35a684170
- **Status:** ✅ Passed
- **Analysis / Findings:** {{TODO:AI_ANALYSIS}}.
---

#### Test TC010 get people search autocomplete endpoint
- **Test Code:** [TC010_get_people_search_autocomplete_endpoint.py](./TC010_get_people_search_autocomplete_endpoint.py)
- **Test Visualization and Result:** https://www.testsprite.com/dashboard/mcp/tests/9e21f103-80ab-4614-a08d-b67ca8c3157a/e07e33a7-36a8-4234-9512-ab1ec914f173
- **Status:** ✅ Passed
- **Analysis / Findings:** {{TODO:AI_ANALYSIS}}.
---


## 3️⃣ Coverage & Matching Metrics

- **90.00** of tests passed

| Requirement        | Total Tests | ✅ Passed | ❌ Failed  |
|--------------------|-------------|-----------|------------|
| ...                | ...         | ...       | ...        |
---


## 4️⃣ Key Gaps / Risks
{AI_GNERATED_KET_GAPS_AND_RISKS}
---