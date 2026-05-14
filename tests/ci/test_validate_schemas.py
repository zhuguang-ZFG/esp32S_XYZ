import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[2] / "tools"))

from validate_schemas import (
    validate_example_against_schema,
    validate_schema_file,
    validate_schema_tree,
)

try:
    from .test_utils import get_fixtures_dir, get_schemas_dir
except ImportError:
    from test_utils import get_fixtures_dir, get_schemas_dir


class TestSchemaValidation(unittest.TestCase):
    def test_validate_schema_file_valid(self):
        schema_path = get_fixtures_dir() / "valid_schema.json"
        result = validate_schema_file(str(schema_path))

        self.assertTrue(result.is_valid)
        self.assertEqual(result.file_path, str(schema_path))
        self.assertEqual(result.error_message, "")

    def test_validate_schema_file_invalid(self):
        schema_path = get_fixtures_dir() / "invalid_schema.json"
        result = validate_schema_file(str(schema_path))

        self.assertFalse(result.is_valid)
        self.assertEqual(result.file_path, str(schema_path))
        self.assertIn("schema error", result.error_message)

    def test_validate_example_against_schema_valid(self):
        schema_path = get_fixtures_dir() / "valid_schema.json"
        example_path = get_fixtures_dir() / "valid_example.json"
        result = validate_example_against_schema(str(example_path), str(schema_path))

        self.assertTrue(result.is_valid)
        self.assertEqual(result.file_path, str(example_path))
        self.assertEqual(result.schema_path, str(schema_path))
        self.assertEqual(result.error_message, "")

    def test_validate_example_against_schema_invalid(self):
        schema_path = get_fixtures_dir() / "valid_schema.json"
        example_path = get_fixtures_dir() / "invalid_example.json"
        result = validate_example_against_schema(str(example_path), str(schema_path))

        self.assertFalse(result.is_valid)
        self.assertEqual(result.file_path, str(example_path))
        self.assertEqual(result.schema_path, str(schema_path))
        self.assertIn("validation failed", result.error_message)

    def test_repository_schema_tree_has_no_errors(self):
        results = validate_schema_tree(get_schemas_dir())
        errors = [result for result in results if not result.is_valid]

        self.assertGreater(len(results), 0)
        self.assertEqual([], [f"{item.file_path}: {item.error_message}" for item in errors])


if __name__ == "__main__":
    unittest.main()
