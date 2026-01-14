#!/usr/bin/env python3
"""Bank of Maharashtra cleanup for CSVs generated from PDF statements."""

from __future__ import annotations

import csv
import re
from datetime import datetime
from pathlib import Path
from typing import List, Optional, Sequence, Tuple

BOM_HEADERS: List[str] = [
    "txn_date",
    "amount",
    "dr_cr_flag",
    "txn_type",
    "txn_ref",
    "payer",
    "description",
]

_BOM_SOURCE_HEADERS: Tuple[str, ...] = (
    "sr_no",
    "date",
    "particulars",
    "cheque_reference_no",
    "debit",
    "credit",
    "balance",
    "channel",
)

_BOM_HEADER_SIGNATURE: Tuple[str, ...] = (
    "srno",
    "date",
    "particulars",
    "chequereferenceno",
    "debit",
    "credit",
    "balance",
    "channel",
)


def _normalize_header_cell(text: str) -> str:
    """Lowercase and strip non-alphanumeric characters to make header detection resilient."""
    return re.sub(r"[^a-z0-9]+", "", text.lower())


def _detect_dialect(csv_path: Path) -> csv.Dialect:
    """Sniff a CSV dialect; fall back to Excel (comma)."""
    with csv_path.open("r", encoding="utf-8", newline="") as source:
        sample = source.read(4096)
        source.seek(0)
        try:
            return csv.Sniffer().sniff(sample, delimiters=",|;\t")
        except csv.Error:
            return csv.get_dialect("excel")


def _find_header_row(rows: Sequence[Sequence[str]]) -> Tuple[int, int]:
    """Locate the BOM header row and return (row_index, data_offset)."""
    offsets = (3, 0)  # Prefer page/table/row offset, but fall back to full row scan.
    for row_index, row in enumerate(rows):
        for offset in offsets:
            normalized = [_normalize_header_cell(cell) for cell in row[offset : offset + len(_BOM_HEADER_SIGNATURE)]]
            if normalized == list(_BOM_HEADER_SIGNATURE)[: len(normalized)] and normalized:
                return row_index, offset
    raise ValueError("BOM header row not found in CSV; cannot clean file.")


def _clean_cell(value: str) -> str:
    """Trim text, collapse whitespace, and treat '-' as empty."""
    text = (value or "").strip()
    if text == "-":
        return ""
    return " ".join(text.split())


def _compute_dr_cr_flag(debit: str, credit: str) -> str:
    """Return debit/credit flag."""
    if debit:
        return "D"
    if credit:
        return "C"
    return ""


def _strip_commas(value: str) -> str:
    """Remove thousand separators from numeric strings."""
    return value.replace(",", "") if value else ""


def _extract_amount(debit: str, credit: str) -> str:
    """Choose the populated amount between debit and credit."""
    if debit:
        return _strip_commas(debit)
    if credit:
        return _strip_commas(credit)
    return ""


def _extract_txn_type(particulars: str) -> str:
    """Use the first four characters of particulars as txn_type."""
    particulars = particulars.strip()
    if not particulars:
        return ""
    return particulars[:4].upper()


def _format_date(date_str: str) -> str:
    """Normalize dates to yyyy-MM-dd when year is first, else dd-MM-yyyy."""
    text = date_str.strip()
    if not text:
        return ""

    parts = re.split(r"[-/.]", text)
    first_is_year = parts and len(parts[0]) == 4 and parts[0].isdigit()

    candidates = ("%d/%m/%Y", "%d-%m-%Y", "%Y-%m-%d", "%Y/%m/%d", "%d/%m/%y", "%Y.%m.%d", "%d.%m.%Y")
    parsed: Optional[datetime] = None
    for fmt in candidates:
        try:
            parsed = datetime.strptime(text, fmt)
            break
        except ValueError:
            continue

    if not parsed:
        return text

    if first_is_year:
        return parsed.strftime("%Y-%m-%d")
    return parsed.strftime("%d-%m-%Y")


def _extract_payer(particulars: str, txn_type: str) -> str:
    """Extract payer text; IMPS pulls value between 4th/5th '/', NEFT/RTGS uses MAH marker."""
    normalized = " ".join(particulars.split())
    if not normalized:
        return ""

    if txn_type == "IMPS":
        compact = re.sub(r"\s*/\s*", "/", normalized)
        parts = compact.split("/")
        if len(parts) >= 5 and parts[4].strip():
            return parts[4].strip()

    if txn_type in {"NEFT", "RTGS"}:
        match = re.search(rf"{txn_type}\s+\S+\s+(.+?)\s+MAH", normalized, flags=re.IGNORECASE)
        if match:
            return match.group(1).strip()
    return ""


def clean_bom_csv(source_csv: Path, target_csv: Optional[Path] = None) -> int:
    """Clean a BOM statement CSV in place (or to a new file). Returns rows written."""
    target_csv = target_csv or source_csv

    if not source_csv.exists():
        raise FileNotFoundError(f"CSV not found: {source_csv}")

    dialect = _detect_dialect(source_csv)
    with source_csv.open("r", encoding="utf-8", newline="") as source:
        reader = csv.reader(source, dialect)
        raw_rows = [row for row in reader]

    if not raw_rows:
        target_csv.write_text("", encoding="utf-8")
        return 0

    header_index, data_offset = _find_header_row(raw_rows)
    cleaned: List[List[str]] = []

    for row in raw_rows[header_index + 1 :]:
        data = row[data_offset : data_offset + len(_BOM_SOURCE_HEADERS)]
        data += [""] * (len(_BOM_SOURCE_HEADERS) - len(data))
        data = [_clean_cell(cell) for cell in data]
        row_map = dict(zip(_BOM_SOURCE_HEADERS, data))

        normalized = [_normalize_header_cell(cell) for cell in data[: len(_BOM_HEADER_SIGNATURE)]]
        if normalized == list(_BOM_HEADER_SIGNATURE):
            continue

        if not any((row_map["date"], row_map["particulars"], row_map["debit"], row_map["credit"])):
            continue

        dr_cr_flag = _compute_dr_cr_flag(row_map["debit"], row_map["credit"])
        amount = _extract_amount(row_map["debit"], row_map["credit"])
        txn_type = _extract_txn_type(row_map["particulars"])
        cleaned.append(
            [
                _format_date(row_map["date"]),
                amount,
                dr_cr_flag,
                txn_type,
                row_map["cheque_reference_no"],
                _extract_payer(row_map["particulars"], txn_type),
                row_map["particulars"],
            ]
        )

    target_csv.parent.mkdir(parents=True, exist_ok=True)
    with target_csv.open("w", encoding="utf-8", newline="") as dest:
        writer = csv.writer(dest)
        writer.writerow(BOM_HEADERS)
        writer.writerows(cleaned)

    return len(cleaned)
