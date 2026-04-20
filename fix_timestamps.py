#!/usr/bin/env python3
import re
import os

files = [
    "src/main/java/com/sub2api/module/account/model/entity/Account.java",
    "src/main/java/com/sub2api/module/account/model/entity/AccountGroup.java",
    "src/main/java/com/sub2api/module/account/model/entity/Group.java",
    "src/main/java/com/sub2api/module/account/model/entity/Proxy.java",
    "src/main/java/com/sub2api/module/admin/model/entity/Announcement.java",
    "src/main/java/com/sub2api/module/admin/model/entity/ErrorPassthroughRule.java",
    "src/main/java/com/sub2api/module/admin/model/entity/IdempotencyRecord.java",
    "src/main/java/com/sub2api/module/admin/model/entity/ScheduledTestPlan.java",
    "src/main/java/com/sub2api/module/admin/model/entity/ScheduledTestResult.java",
    "src/main/java/com/sub2api/module/admin/model/entity/Setting.java",
    "src/main/java/com/sub2api/module/admin/model/entity/TLSFingerprintProfile.java",
    "src/main/java/com/sub2api/module/apikey/model/entity/ApiKey.java",
    "src/main/java/com/sub2api/module/billing/model/entity/PromoCode.java",
    "src/main/java/com/sub2api/module/billing/model/entity/PromoCodeUsage.java",
    "src/main/java/com/sub2api/module/billing/model/entity/RedeemCode.java",
    "src/main/java/com/sub2api/module/billing/model/entity/UsageCleanupFilters.java",
    "src/main/java/com/sub2api/module/billing/model/entity/UsageCleanupTask.java",
    "src/main/java/com/sub2api/module/billing/model/entity/UsageLog.java",
    "src/main/java/com/sub2api/module/channel/model/entity/Channel.java",
    "src/main/java/com/sub2api/module/channel/model/entity/ChannelModelPricing.java",
    "src/main/java/com/sub2api/module/channel/model/entity/PricingInterval.java",
    "src/main/java/com/sub2api/module/dashboard/model/entity/DashboardAggregationWatermark.java",
    "src/main/java/com/sub2api/module/dashboard/model/entity/UsageDashboardDaily.java",
    "src/main/java/com/sub2api/module/dashboard/model/entity/UsageDashboardHourly.java",
    "src/main/java/com/sub2api/module/dashboard/model/entity/UsageDashboardHourlyUsers.java",
    "src/main/java/com/sub2api/module/ops/model/entity/OpsErrorLog.java",
    "src/main/java/com/sub2api/module/user/model/entity/AnnouncementRead.java",
    "src/main/java/com/sub2api/module/user/model/entity/User.java",
    "src/main/java/com/sub2api/module/user/model/entity/UserAllowedGroup.java",
    "src/main/java/com/sub2api/module/user/model/entity/UserSubscription.java",
]

base = os.path.dirname(os.path.abspath(__file__))

for rel_path in files:
    filepath = os.path.join(base, rel_path)
    if not os.path.exists(filepath):
        print(f"SKIP: {filepath} not found")
        continue

    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    original = content

    # Replace import
    content = content.replace('import java.time.LocalDateTime;', 'import java.time.OffsetDateTime;')

    # Replace field types: "private LocalDateTime " -> "private OffsetDateTime "
    content = re.sub(r'private LocalDateTime ', 'private OffsetDateTime ', content)

    # Replace method return types: "public LocalDateTime get" -> "public OffsetDateTime get"
    content = re.sub(r'public LocalDateTime (get\w+)\(', r'public OffsetDateTime \1(', content)

    # Replace setter parameter types: "setXXX(LocalDateTime " -> "setXXX(OffsetDateTime "
    content = re.sub(r'set(\w+)\(LocalDateTime ', r'set\1(OffsetDateTime ', content)

    # Replace return types in method bodies: "LocalDateTime LocalDateTime" patterns in chained calls
    # This handles cases like "setCreatedAt(OffsetDateTime " where "OffsetDateTime" was already partially replaced
    # Actually our regex above handles it. But we need to handle the case where "LocalDateTime" appears as a standalone type.

    if content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"FIXED: {rel_path}")
    else:
        print(f"NO CHANGE: {rel_path}")

print("Done")
