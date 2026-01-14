#!/usr/bin/env python3
"""Bank of Baroda text statement parser."""

from __future__ import annotations

import csv
import re
from dataclasses import dataclass, field
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Iterable, List, Optional, Sequence, Tuple

import pdfplumber

BOB_HEADERS: List[str] = ["txn_date", "amount", "dr_cr_flag", "txn_type", "txn_ref", "payer", "description"]

_DATE_LINE = re.compile(r"^(?P<date>\d{2}-\d{2}-\d{2})\s+(?P<rest>.+)$")
_AMOUNT_TOKEN = re.compile(r"\d{1,3}(?:,\d{3})*\.\d{2}$")
_BALANCE_TAIL = re.compile(r"(?P<balance>[0-9,]+\.\d{2})(?P<flag>Cr|Dr)?$")


@dataclass
class Transaction:
    date: str
    description: str
    balance: Decimal
    amounts: List[Decimal] = field(default_factory=list)


def _format_amount(value: Decimal) -> str:
    return f"{value.quantize(Decimal('0.01'))}"


def _format_date(date_str: str) -> str:
    """Convert dd-mm-yy to dd-MM-yyyy."""
    try:
        parsed = datetime.strptime(date_str.strip(), "%d-%m-%y")
        return parsed.strftime("%d-%m-%Y")
    except (ValueError, TypeError):
        return date_str.strip()


def _extract_lines(pdf_path: Path) -> List[str]:
    with pdfplumber.open(pdf_path) as pdf:
        lines: List[str] = []
        for page in pdf.pages:
            text = page.extract_text() or ""
            lines.extend(text.splitlines())
    return lines


def _is_noise(line: str) -> bool:
    if not line or line.startswith("-"):
        return True
    lowered = line.lower()
    noise_prefixes = (
        "bank of baroda",
        "midc pimpri",
        "address:",
        "helpline",
        "branch phone",
        "micr code",
        "a/c name",
        "a/c number",
        "statement of account",
        "date particulars",
        "note:",
        "unless the constituent",
        "page total",
    )
    return any(lowered.startswith(prefix) for prefix in noise_prefixes)


def _parse_transaction(main_line: str, extra_lines: Sequence[str]) -> Optional[Transaction]:
    m = _DATE_LINE.match(main_line)
    if not m:
        return None

    rest = m.group("rest").strip()
    balance_match = _BALANCE_TAIL.search(rest)
    if not balance_match:
        return None

    try:
        balance = Decimal(balance_match.group("balance").replace(",", ""))
    except InvalidOperation:
        return None
    if (balance_match.group("flag") or "Cr").lower().startswith("d"):
        balance = -balance

    rest_body = rest[: balance_match.start()].strip()

    tokens = rest_body.split()
    amounts: List[Decimal] = []
    while tokens:
        token = tokens[-1]
        if _AMOUNT_TOKEN.fullmatch(token):
            tokens.pop()
            try:
                amounts.insert(0, Decimal(token.replace(",", "")))
            except InvalidOperation:
                pass
            continue
        break

    description = " ".join(tokens).strip()
    if extra_lines:
        description = " ".join([description, *extra_lines]).strip()

    return Transaction(date=m.group("date"), description=description, balance=balance, amounts=amounts)


def _compute_amount_flag(prev_balance: Optional[Decimal], curr_balance: Decimal, amounts: Sequence[Decimal]) -> Tuple[str, Decimal]:
    if prev_balance is not None:
        delta = curr_balance - prev_balance
        if delta > 0:
            return "C", delta
        if delta < 0:
            return "D", -delta
    if len(amounts) >= 2:
        if amounts[0] != 0:
            return "D", amounts[0]
        if amounts[1] != 0:
            return "C", amounts[1]
    elif len(amounts) == 1:
        return "C", amounts[0]
    return "", Decimal("0")


def _extract_txn_type(description: str) -> str:
    desc = " ".join(description.split())
    if not desc:
        return ""

    upper = desc.upper()

    if "IMPS/" in upper:
        return "IMPS"

    if "UPI/" in upper:
        prefix = desc.split("/", 1)[0]
        return prefix[-3:].upper()

    if "NEFT" in upper or "RTGS" in upper:
        parts = desc.split("/", 1)
        if len(parts) >= 2:
            segment = parts[1].strip()
            candidate = segment[:5].upper().strip("- ")
            if candidate:
                return candidate
        if "NEFT" in upper:
            return "NEFT"
        if "RTGS" in upper:
            return "RTGS"

    return desc[:4].upper()


def _extract_txn_ref(description: str, txn_type: str) -> str:
    if txn_type == "RTGS":
        hy_parts = description.split("-")
        if len(hy_parts) >= 2 and hy_parts[-2].strip():
            return hy_parts[-2].strip()

    upi = re.search(r"UPI/(\d+)", description, flags=re.IGNORECASE)
    if upi:
        return upi.group(1)
    imps = re.search(r"IMPS/[0-9]*/(\d+)", description, flags=re.IGNORECASE)
    if imps:
        return imps.group(1)
    neft = re.search(r"NEFT-([A-Z0-9]+)", description, flags=re.IGNORECASE)
    if neft:
        return neft.group(1)
    generic = re.search(r"\b\d{6,}\b", description)
    if generic:
        return generic.group(0)
    return ""


def _extract_payer(description: str, txn_type: str, txn_ref: str) -> str:
    compact = " ".join(description.split())
    if not compact:
        return ""

    if txn_type == "UPI":
        normalized = re.sub(r"\s*/\s*", "/", compact)
        parts = normalized.split("/")
        if len(parts) >= 5 and parts[4].strip():
            return parts[4].strip()

    if txn_ref:
        idx = compact.upper().find(txn_ref.upper())
        if idx != -1:
            after = compact[idx + len(txn_ref) :].lstrip(" -/:")
            return after.strip()

    return ""


def clean_bob_pdf(pdf_path: Path, target_csv: Optional[Path] = None) -> int:
    """Parse a BOB text statement PDF into a normalized CSV."""
    target_csv = target_csv or pdf_path.with_suffix(".csv")
    if not pdf_path.exists():
        raise FileNotFoundError(f"PDF not found: {pdf_path}")

    lines = _extract_lines(pdf_path)

    transactions: List[Transaction] = []
    current_line: Optional[str] = None
    extras: List[str] = []

    for raw_line in lines:
        line = raw_line.strip()
        if _is_noise(line):
            continue
        if _DATE_LINE.match(line):
            if current_line:
                txn = _parse_transaction(current_line, extras)
                if txn:
                    transactions.append(txn)
            current_line = line
            extras = []
        else:
            if current_line:
                extras.append(line)
    if current_line:
        txn = _parse_transaction(current_line, extras)
        if txn:
            transactions.append(txn)

    prev_balance: Optional[Decimal] = None
    cleaned_rows: List[List[str]] = []

    for txn in transactions:
        dr_cr_flag, amount_val = _compute_amount_flag(prev_balance, txn.balance, txn.amounts)
        prev_balance = txn.balance

        if dr_cr_flag == "" and amount_val == 0:
            continue

        txn_type = _extract_txn_type(txn.description)
        txn_ref = _extract_txn_ref(txn.description, txn_type)
        cleaned_rows.append(
            [
                _format_date(txn.date),
                _format_amount(amount_val) if amount_val else "",
                dr_cr_flag,
                txn_type,
                txn_ref,
                _extract_payer(txn.description, txn_type, txn_ref),
                txn.description,
            ]
        )

    target_csv.parent.mkdir(parents=True, exist_ok=True)
    with target_csv.open("w", encoding="utf-8", newline="") as dest:
        writer = csv.writer(dest)
        writer.writerow(BOB_HEADERS)
        writer.writerows(cleaned_rows)

    return len(cleaned_rows)
