"""Consistent SQLite online backup; refuses to overwrite an existing backup."""
import pathlib
import sqlite3
import sys

source = pathlib.Path(sys.argv[1]).resolve(strict=True)
target = pathlib.Path(sys.argv[2]).resolve()
if source == target or target.exists():
    raise SystemExit("Choose a new backup filename; overwriting is refused.")
with sqlite3.connect(source.as_uri() + "?mode=ro", uri=True) as src:
    with sqlite3.connect(target) as dst:
        src.backup(dst)
        result = dst.execute("PRAGMA integrity_check").fetchone()[0]
        if result != "ok":
            raise SystemExit("Backup integrity check failed: " + result)
