#!/usr/bin/env python3
"""Extract tables from a PDF file and write them to a single CSV."""

import argparse
import csv
from pathlib import Path
import sys
from typing import Dict, List, Optional, Tuple

import pdfplumber
from bob_parser import clean_bob_pdf
from bom_parser import clean_bom_csv
from pnb_parser import clean_pnb_csv


def convert_pdf_to_csv(
    pdf_path: Path, csv_path: Path, table_settings: Optional[Dict[str, str]] = None
) -> int:
    """Extract tables and write them to a CSV. Returns the number of data rows written."""
    table_settings = table_settings or {"vertical_strategy": "lines", "horizontal_strategy": "lines"}
    if not pdf_path.exists():
        raise FileNotFoundError(f"PDF not found: {pdf_path}")
    csv_path.parent.mkdir(parents=True, exist_ok=True)

    rows: List[Tuple[int, int, int, List[str]]] = []
    max_cols = 0

    with pdfplumber.open(pdf_path) as pdf:
        for page_index, page in enumerate(pdf.pages, start=1):
            page_tables = page.extract_tables(table_settings=table_settings) or []
            for table_index, table in enumerate(page_tables, start=1):
                for row_index, row in enumerate(table, start=1):
                    cleaned_row = [
                        cell.strip() if isinstance(cell, str) else "" if cell is None else str(cell)
                        for cell in row
                    ]
                    max_cols = max(max_cols, len(cleaned_row))
                    rows.append((page_index, table_index, row_index, cleaned_row))

    with csv_path.open("w", newline="", encoding="utf-8") as csv_file:
        writer = csv.writer(csv_file, delimiter="|")
        header = ["page", "table", "row"] + [f"col_{i}" for i in range(1, max_cols + 1)]
        writer.writerow(header)
        for page_index, table_index, row_index, row in rows:
            normalized_row = row + [""] * (max_cols - len(row))
            writer.writerow([page_index, table_index, row_index, *normalized_row])

    return len(rows)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Extract tables from a PDF into a CSV.")
    parser.add_argument("pdf", type=Path, help="Path to the source PDF file.")
    parser.add_argument("csv", type=Path, help="Destination CSV path.")
    parser.add_argument(
        "--strategy",
        choices=["lines", "text"],
        default="lines",
        help=(
            "Table detection strategy for pdfplumber: 'lines' for ruled tables, "
            "or 'text' for whitespace-separated columns."
        ),
    )
    parser.add_argument(
        "--bank",
        choices=["pnb", "bom", "bob"],
        help="Apply bank-specific cleanup to the generated CSV.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    table_settings = {"vertical_strategy": args.strategy, "horizontal_strategy": args.strategy}
    if args.bank == "bob":
        try:
            cleaned_rows = clean_bob_pdf(pdf_path=args.pdf, target_csv=args.csv)
        except Exception as exc:  # pylint: disable=broad-except
            print(f"Failed to parse {args.pdf}: {exc}", file=sys.stderr)
            sys.exit(1)
        print(f"Applied BOB cleanup directly from PDF. Wrote {cleaned_rows} rows to {args.csv}.")
        return

    try:
        rows = convert_pdf_to_csv(args.pdf, args.csv, table_settings=table_settings)
    except Exception as exc:  # pylint: disable=broad-except
        print(f"Failed to convert {args.pdf}: {exc}", file=sys.stderr)
        sys.exit(1)

    if rows == 0:
        print(f"No tables detected in {args.pdf}. Wrote headers to {args.csv}.")
    else:
        print(f"Wrote {rows} rows to {args.csv} (strategy={args.strategy}).")

    if args.bank:
        if args.bank == "pnb":
            cleaned_rows = clean_pnb_csv(source_csv=args.csv, target_csv=args.csv)
        elif args.bank == "bom":
            cleaned_rows = clean_bom_csv(source_csv=args.csv, target_csv=args.csv)
        else:
            raise ValueError(f"Unsupported bank: {args.bank}")
        print(f"Applied {args.bank.upper()} cleanup. Wrote {cleaned_rows} rows to {args.csv}.")


if __name__ == "__main__":
    main()
