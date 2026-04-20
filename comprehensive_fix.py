#!/usr/bin/env python3
"""Comprehensive fix: replace all LocalDateTime with OffsetDateTime except in DateTimeUtil."""
import re
from pathlib import Path

base = Path(__file__).parent
java_files = list(base.glob("src/main/java/**/*.java"))

for f in java_files:
    if 'DateTimeUtil.java' in str(f):
        continue  # Keep DateTimeUtil as LocalDateTime

    try:
        content = f.read_text(encoding='utf-8')
        original = content

        # Replace import
        content = re.sub(r'import java\.time\.LocalDateTime;', 'import java.time.OffsetDateTime;', content)

        # Replace all LocalDateTime references
        # These need careful ordering to avoid replacing partial matches

        # First, static factory methods - most specific first
        content = re.sub(r'LocalDateTime\.of\b', 'OffsetDateTime.of', content)
        content = re.sub(r'LocalDateTime\.now\b', 'OffsetDateTime.now', content)
        content = re.sub(r'LocalDateTime\.parse\b', 'OffsetDateTime.parse', content)
        content = re.sub(r'LocalDateTime\.from\b', 'OffsetDateTime.from', content)
        content = re.sub(r'LocalDateTime\.ofInstant\b', 'OffsetDateTime.ofInstant', content)
        content = re.sub(r'LocalDateTime\.ofEpochSecond\b', 'OffsetDateTime.ofEpochSecond', content)

        # Now general replacements - ensure we don't break other identifiers
        # Replace "LocalDateTime " (with space after) - parameter and variable types
        content = re.sub(r'\bLocalDateTime ', 'OffsetDateTime ', content)
        # Replace "LocalDateTime," (with comma after) - generic type parameters
        content = re.sub(r'LocalDateTime,', 'OffsetDateTime,', content)
        # Replace "LocalDateTime>" (with > after) - generic type close
        content = re.sub(r'LocalDateTime>', 'OffsetDateTime>', content)
        # Replace "<LocalDateTime" - generic type open
        content = re.sub(r'<LocalDateTime', '<OffsetDateTime', content)
        # Replace "LocalDateTime)" - at end of parameter list
        content = re.sub(r'\bLocalDateTime\)', 'OffsetDateTime)', content)
        # Replace "(LocalDateTime." - cast or method call with dot
        content = re.sub(r'\(LocalDateTime\.', '(OffsetDateTime.', content)
        # Replace "LocalDateTime." - any other method call
        content = re.sub(r'LocalDateTime\.', 'OffsetDateTime.', content)
        # Replace ".LocalDateTime" - any remaining occurrences
        content = re.sub(r'\.LocalDateTime', '.OffsetDateTime', content)
        # Replace "LocalDateTime(" - constructor or method call
        content = re.sub(r'LocalDateTime\(', 'OffsetDateTime(', content)

        if content != original:
            f.write_text(content, encoding='utf-8')
            print(f"FIXED: {f.relative_to(base)}")
    except Exception as e:
        print(f"ERROR: {f}: {e}")

print("\nDone")