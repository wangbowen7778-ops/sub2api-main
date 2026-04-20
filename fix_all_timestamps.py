#!/usr/bin/env python3
"""Fix LocalDateTime -> OffsetDateTime in all Java files."""
import re
import os
from pathlib import Path

base = Path(__file__).parent
java_files = list(base.glob("src/main/java/**/*.java"))

replacements = [
    # Import replacement (add after existing imports)
    (r'import java\.time\.LocalDateTime;', 'import java.time.OffsetDateTime;'),
    # Field type replacement
    (r'private LocalDateTime ', 'private OffsetDateTime '),
    # Method return type replacement
    (r'public LocalDateTime (get\w+)\(', r'public OffsetDateTime \1('),
    # Setter parameter type replacement
    (r'set(\w+)\(LocalDateTime ', r'set\1(OffsetDateTime '),
    # Method body LocalDateTime references
    (r'LocalDateTime\.now\(\)', 'OffsetDateTime.now()'),
    (r'LocalDateTime\.of\(', 'OffsetDateTime.ofLocalDateTime('),
    (r'LocalDateTime ', 'OffsetDateTime '),
]

fixed_files = []
error_files = []

for f in java_files:
    try:
        content = f.read_text(encoding='utf-8')
        original = content

        # Skip if already has OffsetDateTime import
        if 'import java.time.OffsetDateTime;' in content:
            continue

        for pattern, replacement in replacements:
            content = re.sub(pattern, replacement, content)

        if content != original:
            f.write_text(content, encoding='utf-8')
            fixed_files.append(str(f.relative_to(base)))
    except Exception as e:
        error_files.append((str(f.relative_to(base)), str(e)))

print(f"Fixed {len(fixed_files)} files:")
for fn in fixed_files:
    print(f"  {fn}")

if error_files:
    print(f"\nErrors:")
    for fn, err in error_files:
        print(f"  {fn}: {err}")

print(f"\nTotal: {len(fixed_files)} fixed, {len(error_files)} errors")