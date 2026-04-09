"""Pandas Excel reads with openpyxl; suppresses known OOXML extension UserWarnings."""

from __future__ import annotations

import warnings
from contextlib import contextmanager

import pandas as pd


@contextmanager
def suppress_openpyxl_extension_warnings():
    with warnings.catch_warnings():
        warnings.filterwarnings(
            "ignore",
            category=UserWarning,
            message=r".*extension is not supported and will be removed",
        )
        yield


def read_excel(*args, **kwargs):
    with suppress_openpyxl_extension_warnings():
        return pd.read_excel(*args, **kwargs)


def open_excel_file(*args, **kwargs):
    with suppress_openpyxl_extension_warnings():
        return pd.ExcelFile(*args, **kwargs)
