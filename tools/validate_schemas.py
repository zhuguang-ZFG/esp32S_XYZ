#!/usr/bin/env python3
"""Validate JSON Schema contracts and their examples."""

from __future__ import annotations

import json
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, List, Sequence

try:
    import jsonschema
    from jsonschema import Draft202012Validator, ValidationError
except ImportError:
    print("ERROR: missing dependency: jsonschema")
    print("Install with: pip install jsonschema")
    sys.exit(1)


@dataclass
class SchemaValidationResult:
    file_path: str
    is_valid: bool
    error_message: str = ""
    schema_path: str = ""


def _load_json(path: Path) -> object:
    with path.open("r", encoding="utf-8-sig") as fh:
        return json.load(fh)


def validate_schema_file(schema_path: str) -> SchemaValidationResult:
    path = Path(schema_path)
    try:
        schema = _load_json(path)
        Draft202012Validator.check_schema(schema)
        return SchemaValidationResult(file_path=str(path), is_valid=True)
    except json.JSONDecodeError as exc:
        return SchemaValidationResult(
            file_path=str(path),
            is_valid=False,
            error_message=f"JSON parse error: {exc.msg} (line {exc.lineno}, column {exc.colno})",
        )
    except jsonschema.SchemaError as exc:
        return SchemaValidationResult(
            file_path=str(path),
            is_valid=False,
            error_message=f"schema error: {exc.message}",
        )
    except FileNotFoundError:
        return SchemaValidationResult(
            file_path=str(path),
            is_valid=False,
            error_message="file not found",
        )
    except Exception as exc:  # pragma: no cover - defensive CLI guard
        return SchemaValidationResult(
            file_path=str(path),
            is_valid=False,
            error_message=f"unexpected error: {exc}",
        )


def validate_example_against_schema(example_path: str, schema_path: str) -> SchemaValidationResult:
    example = Path(example_path)
    schema_file = Path(schema_path)
    try:
        schema = _load_json(schema_file)
        instance = _load_json(example)
        Draft202012Validator(schema).validate(instance)
        return SchemaValidationResult(
            file_path=str(example),
            is_valid=True,
            schema_path=str(schema_file),
        )
    except json.JSONDecodeError as exc:
        return SchemaValidationResult(
            file_path=str(example),
            is_valid=False,
            error_message=f"JSON parse error: {exc.msg} (line {exc.lineno}, column {exc.colno})",
            schema_path=str(schema_file),
        )
    except ValidationError as exc:
        field_path = ".".join(str(part) for part in exc.path) if exc.path else "<root>"
        return SchemaValidationResult(
            file_path=str(example),
            is_valid=False,
            error_message=f"validation failed at {field_path}: {exc.message}",
            schema_path=str(schema_file),
        )
    except FileNotFoundError as exc:
        return SchemaValidationResult(
            file_path=str(example),
            is_valid=False,
            error_message=f"file not found: {exc.filename}",
            schema_path=str(schema_file),
        )
    except Exception as exc:  # pragma: no cover - defensive CLI guard
        return SchemaValidationResult(
            file_path=str(example),
            is_valid=False,
            error_message=f"unexpected error: {exc}",
            schema_path=str(schema_file),
        )


def find_schema_files(schemas_dir: Path) -> List[Path]:
    return sorted(schemas_dir.rglob("*.schema.json"))


def find_example_files(schema_file: Path) -> List[Path]:
    examples_dir = schema_file.parent / "examples"
    if not examples_dir.exists():
        return []
    return sorted(path for path in examples_dir.glob("*.json") if not path.name.endswith(".schema.json"))


def _edge_dirs(schema_files: Sequence[Path]) -> List[Path]:
    return sorted({schema.parent for schema in schema_files})


def _matching_schemas(example_file: Path, schema_files: Iterable[Path]) -> List[Path]:
    matches: List[Path] = []
    for schema_file in schema_files:
        result = validate_example_against_schema(str(example_file), str(schema_file))
        if result.is_valid:
            matches.append(schema_file)
    return matches


def validate_schema_tree(schemas_dir: Path) -> List[SchemaValidationResult]:
    schema_files = find_schema_files(schemas_dir)
    results: List[SchemaValidationResult] = []

    for schema_file in schema_files:
        results.append(validate_schema_file(str(schema_file)))

    valid_schema_files = [Path(result.file_path) for result in results if result.is_valid]
    for edge_dir in _edge_dirs(valid_schema_files):
        edge_schema_files = [schema for schema in valid_schema_files if schema.parent == edge_dir]
        examples_dir = edge_dir / "examples"
        if not examples_dir.exists():
            continue

        for example_file in sorted(examples_dir.glob("*.json")):
            if example_file.name.endswith(".schema.json"):
                continue
            matches = _matching_schemas(example_file, edge_schema_files)
            if len(matches) == 1:
                results.append(
                    SchemaValidationResult(
                        file_path=str(example_file),
                        is_valid=True,
                        schema_path=str(matches[0]),
                    )
                )
            elif len(matches) == 0:
                results.append(
                    SchemaValidationResult(
                        file_path=str(example_file),
                        is_valid=False,
                        error_message="example does not match any schema in its edge directory",
                    )
                )
            else:
                rel_matches = ", ".join(str(path.name) for path in matches)
                results.append(
                    SchemaValidationResult(
                        file_path=str(example_file),
                        is_valid=False,
                        error_message=f"example matches multiple schemas: {rel_matches}",
                    )
                )

    return results


def main() -> None:
    project_root = Path(__file__).resolve().parent.parent
    schemas_dir = project_root / "docs" / "schemas"

    if not schemas_dir.exists():
        print(f"ERROR: schema directory not found: {schemas_dir}")
        sys.exit(1)

    results = validate_schema_tree(schemas_dir)
    if not results:
        print(f"WARN: no schemas found under {schemas_dir}")
        sys.exit(0)

    errors = [result for result in results if not result.is_valid]
    for result in results:
        rel_path = Path(result.file_path).relative_to(project_root)
        if result.is_valid:
            if result.schema_path:
                rel_schema = Path(result.schema_path).relative_to(project_root)
                print(f"OK   {rel_path} -> {rel_schema}")
            else:
                print(f"OK   {rel_path}")
        else:
            print(f"FAIL {rel_path}: {result.error_message}")

    print("-" * 60)
    print(f"validated={len(results)} passed={len(results) - len(errors)} failed={len(errors)}")
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
