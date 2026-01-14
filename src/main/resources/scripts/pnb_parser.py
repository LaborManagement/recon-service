#!/usr/bin/env python3
"""PNB-specific cleanup for CSVs generated from PDF statements."""

from __future__ import annotations

import csv
import re
from pathlib import Path
from typing import List, Optional, Sequence, Tuple

PNB_HEADERS: List[str] = ["txn_date", "amount", "dr_cr_flag", "txn_type", "txn_ref", "payer", "description"]

# Expected columns as they appear in the source CSV (after page/table/row offsets).
_PNB_SOURCE_HEADERS: Tuple[str, ...] = (
    "tran_date",
    "withdrawal",
    "deposit",
    "balance",
    "alpha",
    "cheque_no",
    "narration",
    "additional_info",
)

# Normalized header signature (letters/numbers only, lowercase).
_PNB_HEADER_SIGNATURE: Tuple[str, ...] = (
    "trandate",
    "withdrawal",
    "deposit",
    "balance",
    "alpha",
    "chqno",
    "narration",
    "additionalinfo",
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
    """Locate the PNB header row and return (row_index, data_offset)."""
    offsets = (3, 0)  # Prefer page/table/row offset, but fall back to full row scan.
    for row_index, row in enumerate(rows):
        for offset in offsets:
            normalized = [_normalize_header_cell(cell) for cell in row[offset : offset + len(_PNB_HEADER_SIGNATURE)]]
            if normalized == list(_PNB_HEADER_SIGNATURE)[: len(normalized)] and normalized:
                return row_index, offset
    raise ValueError("PNB header row not found in CSV; cannot clean file.")


def _compute_dr_cr_flag(withdrawal: str, deposit: str) -> str:
    """Return debit/credit flag."""
    if withdrawal:
        return "D"
    if deposit:
        return "C"
    return ""


def _extract_txn_type(narration: str) -> str:
    """Derive transaction type from the first four characters of narration."""
    narration = narration.strip()
    if not narration:
        return ""
    prefix = narration[:4].upper()
    if prefix.startswith("UPI"):
        return "UPI"
    return prefix


def _extract_txn_ref(narration: str, txn_type: str) -> str:
    """Extract transaction reference based on txn type."""
    text = narration.strip()
    if not text:
        return ""

    if txn_type in {"NEFT", "RTGS"}:
        parts = text.split(":")
        if len(parts) >= 3:
            return parts[1].strip()
        return ""

    if txn_type == "IMPS":
        parts = text.split("/")
        if len(parts) >= 3:
            return parts[1].strip()
        return ""

    return ""


def _extract_payer(narration: str, txn_type: str) -> str:
    """Return payer text."""
    normalized = " ".join(narration.split())

    if txn_type in {"NEFT", "RTGS"}:
        # Many NEFT/RTGS narrations look like "NEFT <ref> <payer> MAH...".
        match = re.search(rf"{txn_type}\s+\S+\s+(.+?)\s+MAH", normalized, flags=re.IGNORECASE)
        if match:
            return match.group(1).strip()

    if txn_type == "RTGS":
        parts = normalized.split(":")
        if len(parts) >= 4:
            return ":".join(parts[3:]).strip()
        return ""

    first_colon = normalized.find(":")
    if first_colon == -1:
        return ""
    second_colon = normalized.find(":", first_colon + 1)
    if second_colon == -1:
        return ""
    return normalized[second_colon + 1 :].strip()


def clean_pnb_csv(source_csv: Path, target_csv: Optional[Path] = None) -> int:
    """Clean a PNB statement CSV in place (or to a new file). Returns rows written."""
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
        data = row[data_offset : data_offset + len(_PNB_HEADER_SIGNATURE)]
        data += [""] * (len(_PNB_HEADER_SIGNATURE) - len(data))
        data = [cell.strip() for cell in data]
        row_map = dict(zip(_PNB_SOURCE_HEADERS, data))

        if row_map["tran_date"].strip().lower().startswith("page total"):
            continue

        txn_type = _extract_txn_type(row_map["narration"])
        txn_ref = _extract_txn_ref(row_map["narration"], txn_type)
        if row_map["cheque_no"]:
            txn_ref = row_map["cheque_no"]
        dr_cr_flag = _compute_dr_cr_flag(row_map["withdrawal"], row_map["deposit"])
        amount = ""
        if dr_cr_flag == "D":
            amount = row_map["withdrawal"]
        elif dr_cr_flag == "C":
            amount = row_map["deposit"]
        output_row = [
            row_map["tran_date"],
            amount,
            dr_cr_flag,
            txn_type,
            txn_ref,
            _extract_payer(row_map["narration"], txn_type),
            row_map["narration"],
        ]
        if all(cell == "" for cell in output_row):
            continue
        cleaned.append(output_row)

    target_csv.parent.mkdir(parents=True, exist_ok=True)
    with target_csv.open("w", encoding="utf-8", newline="") as dest:
        writer = csv.writer(dest)
        writer.writerow(PNB_HEADERS)
        writer.writerows(cleaned)

    return len(cleaned)
