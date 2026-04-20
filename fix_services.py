#!/usr/bin/env python3
"""Fix LocalDateTime -> OffsetDateTime in service files that interact with entities."""
import re
import os
from pathlib import Path

base = Path(__file__).parent

# Files to fix - service files that call entity setters with LocalDateTime.now()
files_to_fix = [
    "src/main/java/com/sub2api/module/account/service/GroupService.java",
    "src/main/java/com/sub2api/module/account/service/ProxyConfigService.java",
    "src/main/java/com/sub2api/module/account/service/scheduler/RedisSchedulerCache.java",
    "src/main/java/com/sub2api/module/admin/service/AnnouncementService.java",
    "src/main/java/com/sub2api/module/apikey/service/ApiKeyService.java",
    "src/main/java/com/sub2api/module/admin/service/IdempotencyService.java",
    "src/main/java/com/sub2api/module/admin/service/ErrorPassthroughRuleService.java",
    "src/main/java/com/sub2api/module/admin/service/IdempotencyCleanupService.java",
    "src/main/java/com/sub2api/module/admin/service/AdminService.java",
    "src/main/java/com/sub2api/module/admin/service/SettingService.java",
    "src/main/java/com/sub2api/module/admin/service/ScheduledTestService.java",
    "src/main/java/com/sub2api/module/admin/service/TLSFingerprintProfileService.java",
    "src/main/java/com/sub2api/module/apikey/service/ApiKeyCacheService.java",
    "src/main/java/com/sub2api/module/apikey/service/RedisApiKeyAuthCache.java",
    "src/main/java/com/sub2api/module/account/service/AccountService.java",
    "src/main/java/com/sub2api/module/account/service/AccountExpiryService.java",
    "src/main/java/com/sub2api/module/account/service/AccountHealthService.java",
    "src/main/java/com/sub2api/module/account/service/AccountRefreshService.java",
    "src/main/java/com/sub2api/module/account/service/AccountSelector.java",
    "src/main/java/com/sub2api/module/account/service/DeferredService.java",
    "src/main/java/com/sub2api/module/account/service/scheduler/SchedulerSnapshotService.java",
    "src/main/java/com/sub2api/module/admin/mapper/IdempotencyRecordMapper.java",
    "src/main/java/com/sub2api/module/admin/service/SystemOperationLockService.java",
]

for rel_path in files_to_fix:
    filepath = base / rel_path
    if not filepath.exists():
        print(f"SKIP: {rel_path} not found")
        continue

    try:
        content = filepath.read_text(encoding='utf-8')
        original = content

        # Replace import
        content = re.sub(r'import java\.time\.LocalDateTime;', 'import java.time.OffsetDateTime;', content)

        # Replace LocalDateTime.now() with OffsetDateTime.now()
        content = re.sub(r'LocalDateTime\.now\(\)', 'OffsetDateTime.now()', content)

        # Replace LocalDateTime.of() patterns - these need careful handling
        # For cases like LocalDateTime.of(2024, 1, 1, 0, 0), convert to OffsetDateTime
        # The simplest approach: replace "LocalDateTime.of" with "OffsetDateTime.ofLocalDateTime"
        # But OffsetDateTime.of takes (localDateTime, zoneOffset) - need to add UTC
        content = re.sub(
            r'LocalDateTime\.of\((\d+),\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+)(,\s*\d+)?\)',
            r'OffsetDateTime.of(\1, \2, \3, \4, \5\6, 0, java.time.ZoneOffset.UTC)',
            content
        )

        # Handle "new LocalDateTime" or "new LocalDateTime()" - unlikely but check
        content = re.sub(r'new LocalDateTime\(', 'new OffsetDateTime(', content)

        if content != original:
            filepath.write_text(content, encoding='utf-8')
            print(f"FIXED: {rel_path}")
        else:
            print(f"NO CHANGE: {rel_path}")
    except Exception as e:
        print(f"ERROR: {rel_path}: {e}")

print("\nDone")