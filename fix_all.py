#!/usr/bin/env python3
"""Fix all LocalDateTime -> OffsetDateTime in backend-java source files."""
import re
from pathlib import Path

base = Path(__file__).parent
java_files = list(base.glob("src/main/java/**/*.java"))

# Patterns to replace
patterns = [
    # Generic types: Map<Long, LocalDateTime> -> Map<Long, OffsetDateTime>
    (r'LocalDateTime\.class\b', 'OffsetDateTime.class'),
    (r'java\.util\.Map<([^>]+),\s*LocalDateTime>', r'java.util.Map<\1, OffsetDateTime>'),
    (r'Map<([^>]+),\s*LocalDateTime>', r'Map<\1, OffsetDateTime>'),
    # Constructor/method calls
    (r'LocalDateTime\.of\(', 'OffsetDateTime.ofLocalDateTime('),
    (r'new LocalDateTime\(', 'new OffsetDateTime('),
    (r'LocalDateTime\.now\(\)', 'OffsetDateTime.now()'),
    (r'LocalDateTime\.parse\(', 'OffsetDateTime.parse('),
    (r'LocalDateTime\.from\(', 'OffsetDateTime.from('),
    # Variable declarations and parameters
    (r'LocalDateTime ', 'OffsetDateTime '),
    (r'LocalDateTime,', 'OffsetDateTime,'),
    (r'LocalDateTime>', 'OffsetDateTime>'),
    (r'LocalDateTime<', 'OffsetDateTime<'),
    (r'\bLocalDateTime\)', 'OffsetDateTime)'),
    (r'\(LocalDateTime\.', '(OffsetDateTime.'),
]

fixed = []
errors = []

for f in java_files:
    try:
        content = f.read_text(encoding='utf-8')
        original = content

        # Skip DateTimeUtil - we keep it as LocalDateTime internally
        if 'DateTimeUtil.java' in str(f):
            continue

        for pattern, replacement in patterns:
            content = re.sub(pattern, replacement, content)

        if content != original:
            f.write_text(content, encoding='utf-8')
            fixed.append(str(f.relative_to(base)))
    except Exception as e:
        errors.append((str(f.relative_to(base)), str(e)))

print(f"Fixed {len(fixed)} files")
for fn in fixed:
    print(f"  {fn}")
if errors:
    print(f"\nErrors:")
    for fn, err in errors:
        print(f"  {fn}: {err}")