import os
import sys
import unittest
from unittest.mock import patch

import pandas as pd


CURRENT_DIR = os.path.dirname(__file__)
PYTHON_ROOT = os.path.abspath(os.path.join(CURRENT_DIR, ".."))
if PYTHON_ROOT not in sys.path:
    sys.path.insert(0, PYTHON_ROOT)

from processors.objects import process_bulk_processor


def _lookup_side_effect(table, value):
    mapping = {
        ("process_type", "Core"): 101,
        ("process_lifecycle_status", "Active"): 102,
        ("process_step_type", "Manual"): 103,
        ("process_duration_type", "Hours"): 104,
    }
    return mapping.get((table, value))


class ProcessDurationValidationTests(unittest.TestCase):
    @patch("processors.objects.process_bulk_processor.add_custom_fields_to_validated_data", new=None)
    @patch("processors.objects.process_bulk_processor.check_duplicate_primary_name", return_value=False)
    @patch("processors.objects.process_bulk_processor.get_lookup_id_by_name", side_effect=_lookup_side_effect)
    def test_create_invalid_duration_excludes_row(self, _mock_lookup, _mock_dup):
        df = pd.DataFrame([
            {
                "Name": "Proc A",
                "Description": "desc",
                "Step Type": "Manual",
                "Type": "Core",
                "Lifecycle": "Active",
                "Duration": "not-a-number",
                "Duration Type": "Hours",
            }
        ])

        errors, data = process_bulk_processor.validate_row_data(df, "Add New Items")

        self.assertEqual(len(data), 0)
        self.assertTrue(any(e.field == "Duration" and e.error_code == "INVALID_VALUE" for e in errors))

    @patch("processors.objects.process_bulk_processor.add_custom_fields_to_validated_data", new=None)
    @patch("processors.objects.process_bulk_processor.check_duplicate_primary_name", return_value=False)
    @patch("processors.objects.process_bulk_processor.get_lookup_id_by_name", side_effect=_lookup_side_effect)
    def test_create_invalid_duration_type_excludes_row(self, _mock_lookup, _mock_dup):
        df = pd.DataFrame([
            {
                "Name": "Proc B",
                "Description": "desc",
                "Step Type": "Manual",
                "Type": "Core",
                "Lifecycle": "Active",
                "Duration": "4",
                "Duration Type": "InvalidType",
            }
        ])

        errors, data = process_bulk_processor.validate_row_data(df, "Add New Items")

        self.assertEqual(len(data), 0)
        self.assertTrue(any(e.field == "Duration Type" and e.error_code == "NOT_FOUND" for e in errors))

    @patch("processors.objects.process_bulk_processor.add_custom_fields_to_validated_data", new=None)
    @patch("processors.objects.process_bulk_processor.check_duplicate_primary_name", return_value=False)
    @patch("processors.objects.process_bulk_processor.get_lookup_id_by_name", side_effect=_lookup_side_effect)
    def test_create_valid_duration_and_type_includes_row(self, _mock_lookup, _mock_dup):
        df = pd.DataFrame([
            {
                "Name": "Proc C",
                "Description": "desc",
                "Step Type": "Manual",
                "Type": "Core",
                "Lifecycle": "Active",
                "Duration": "8",
                "Duration Type": "Hours",
            }
        ])

        errors, data = process_bulk_processor.validate_row_data(df, "Add New Items")

        self.assertEqual(errors, [])
        self.assertEqual(len(data), 1)
        self.assertEqual(data[0]["Duration"], 8)
        self.assertEqual(data[0]["Duration Type_ID"], 104)

    @patch("processors.objects.process_bulk_processor.add_custom_fields_to_validated_data", new=None)
    @patch("processors.objects.process_bulk_processor._exists_in_table", return_value=True)
    @patch("processors.objects.process_bulk_processor.get_lookup_id_by_name", side_effect=_lookup_side_effect)
    def test_update_invalid_duration_type_excludes_row(self, _mock_lookup, _mock_exists):
        df = pd.DataFrame([
            {
                "ID": 55,
                "Duration": "3",
                "Duration Type": "BadType",
            }
        ])

        errors, data = process_bulk_processor.validate_row_data(df, "Update Existing Items")

        self.assertEqual(len(data), 0)
        self.assertTrue(any(e.field == "Duration Type" and e.error_code == "NOT_FOUND" for e in errors))


if __name__ == "__main__":
    unittest.main()
