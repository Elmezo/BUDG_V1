import os
import sys
import unittest
from unittest.mock import patch

import pandas as pd


CURRENT_DIR = os.path.dirname(__file__)
PYTHON_ROOT = os.path.abspath(os.path.join(CURRENT_DIR, ".."))
if PYTHON_ROOT not in sys.path:
    sys.path.insert(0, PYTHON_ROOT)

from processors.objects import committee_bulk_processor
from processors.objects import policy_bulk_processor


class CommitteePolicyMandatoryValidationTests(unittest.TestCase):
    @patch("processors.objects.committee_bulk_processor.add_custom_fields_to_validated_data", new=None)
    @patch("processors.objects.committee_bulk_processor.check_duplicate_committee_name", return_value=False)
    @patch("processors.objects.committee_bulk_processor.get_lookup_id_by_name", return_value=None)
    def test_committee_create_rejects_empty_mandatory_lookup_fields(self, _mock_lookup, _mock_dup):
        df = pd.DataFrame([
            {
                "Committee Name": "Risk Committee",
                "Description": "desc",
                "Classification": "",
                "Lifecycle": "",
                "Committee Type": "",
            }
        ])

        errors, data = committee_bulk_processor.validate_row_data(df, "Add New Items")

        self.assertEqual(len(data), 0)
        self.assertEqual(
            sorted([e.field for e in errors if e.error_code == "REQUIRED_FIELD_EMPTY"]),
            ["Classification", "Committee Type", "Lifecycle"],
        )

    @patch("processors.objects.committee_bulk_processor.add_custom_fields_to_validated_data", new=None)
    @patch("processors.objects.committee_bulk_processor.check_duplicate_committee_name", return_value=False)
    @patch("processors.objects.committee_bulk_processor.get_lookup_id_by_name")
    def test_committee_create_accepts_row_when_mandatory_fields_present(self, mock_lookup, _mock_dup):
        def lookup_side_effect(table, value):
            mapping = {
                ("committee_classification", "Steering"): 11,
                ("committee_lifecycle", "Active"): 12,
                ("committee_type", "Governance"): 13,
            }
            return mapping.get((table, value))

        mock_lookup.side_effect = lookup_side_effect

        df = pd.DataFrame([
            {
                "Committee Name": "Risk Committee",
                "Description": "desc",
                "Classification": "Steering",
                "Lifecycle": "Active",
                "Committee Type": "Governance",
            }
        ])

        errors, data = committee_bulk_processor.validate_row_data(df, "Add New Items")

        self.assertEqual(errors, [])
        self.assertEqual(len(data), 1)
        self.assertEqual(data[0]["Classification_ID"], 11)
        self.assertEqual(data[0]["Lifecycle_ID"], 12)
        self.assertEqual(data[0]["Committee Type_ID"], 13)

    @patch("processors.objects.policy_bulk_processor.validate_custom_fields_for_row", new=None)
    @patch("processors.objects.policy_bulk_processor.check_duplicate_primary_name", return_value=False)
    def test_policy_create_rejects_empty_lifecycle_and_type(self, _mock_dup):
        df = pd.DataFrame([
            {
                "Name": "InfoSec Policy",
                "Internal": "true",
                "Description": "desc",
                "Lifecycle": "",
                "Type": "",
            }
        ])

        errors, data = policy_bulk_processor.validate_row_data(df, "Add New Items")

        self.assertEqual(len(data), 0)
        mandatory_errors = [e for e in errors if e.error_code == "REQUIRED_FIELD_EMPTY"]
        self.assertEqual(sorted([e.field for e in mandatory_errors]), ["Lifecycle"])

    @patch("processors.objects.policy_bulk_processor.validate_custom_fields_for_row", new=None)
    @patch("processors.objects.policy_bulk_processor.check_duplicate_primary_name", return_value=False)
    @patch("processors.objects.policy_bulk_processor.get_all_lookup_values")
    @patch("processors.objects.policy_bulk_processor.get_lookup_id_by_name")
    def test_policy_create_accepts_row_when_lifecycle_type_present(
        self,
        mock_lookup,
        mock_all_values,
        _mock_dup,
    ):
        def all_values_side_effect(table):
            if table == "policy_lifecycle_status":
                return ["Draft", "Approved"]
            if table == "policy_type":
                return ["Standard", "Guideline"]
            return []

        def lookup_side_effect(table, value):
            mapping = {
                ("policy_lifecycle_status", "Draft"): 21,
                ("policy_type", "Standard"): 22,
            }
            return mapping.get((table, value))

        mock_all_values.side_effect = all_values_side_effect
        mock_lookup.side_effect = lookup_side_effect

        df = pd.DataFrame([
            {
                "Name": "InfoSec Policy",
                "Internal": "true",
                "Description": "desc",
                "Lifecycle": "Draft",
                "Type": "Standard",
            }
        ])

        errors, data = policy_bulk_processor.validate_row_data(df, "Add New Items")

        self.assertEqual(errors, [])
        self.assertEqual(len(data), 1)
        self.assertEqual(data[0]["Lifecycle_ID"], 21)
        self.assertEqual(data[0]["Type_ID"], 22)


if __name__ == "__main__":
    unittest.main()
