param(
    [string]$ProjectRoot = ".",
    [string]$InputWhitelistPath = "dataset/slot_whitelist.json",
    [string]$InputToolsMdPath = "dataset/mcp_tools_list.md",
    [string]$OutputPath = "dataset/stable_slot_whitelist.json"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Add-Mapping {
    param(
        [hashtable]$Map,
        [string]$Canonical,
        [string[]]$Aliases
    )
    foreach ($a in $Aliases) {
        if ([string]::IsNullOrWhiteSpace($a)) { continue }
        $Map[$a.Trim()] = $Canonical
    }
}

$root = (Resolve-Path $ProjectRoot).Path
$inputWhitelist = Join-Path $root $InputWhitelistPath
$inputToolsMd = Join-Path $root $InputToolsMdPath
$outputFile = Join-Path $root $OutputPath

if (-not (Test-Path $inputWhitelist)) {
    throw "Input whitelist not found: $inputWhitelist"
}

$j = Get-Content -Raw -Path $inputWhitelist | ConvertFrom-Json
$rawWhitelist = @($j.whitelist)

# 部分槽位归一化（保守策略：只合并高置信同义项）
$aliasToCanonical = @{}

Add-Mapping -Map $aliasToCanonical -Canonical "query" -Aliases @("query", "keyword", "keywords", "query_text", "ref_name")
Add-Mapping -Map $aliasToCanonical -Canonical "url" -Aliases @("url", "link")
Add-Mapping -Map $aliasToCanonical -Canonical "image_url" -Aliases @("img_url", "image_url", "keep_img_data_url")
Add-Mapping -Map $aliasToCanonical -Canonical "origin" -Aliases @("origin", "origins", "from", "from_city", "dep", "dep_city", "depcity")
Add-Mapping -Map $aliasToCanonical -Canonical "destination" -Aliases @("destination", "to", "to_city", "arr", "arr_city", "arrcity")
Add-Mapping -Map $aliasToCanonical -Canonical "departure_date" -Aliases @("dep_date", "depdate")
Add-Mapping -Map $aliasToCanonical -Canonical "date" -Aliases @("date", "day")
Add-Mapping -Map $aliasToCanonical -Canonical "city" -Aliases @("city", "cityName", "cityd")
Add-Mapping -Map $aliasToCanonical -Canonical "province" -Aliases @("province", "provinceName")
Add-Mapping -Map $aliasToCanonical -Canonical "org_name" -Aliases @("org_name", "orgName")
Add-Mapping -Map $aliasToCanonical -Canonical "shipper_code" -Aliases @("shipper_code", "shipperCode", "expressCode")
Add-Mapping -Map $aliasToCanonical -Canonical "tracking_number" -Aliases @("express_no", "refernumber")
Add-Mapping -Map $aliasToCanonical -Canonical "issue_no" -Aliases @("issue_no", "issueno", "lottery_number")
Add-Mapping -Map $aliasToCanonical -Canonical "lottery_id" -Aliases @("lottery_id", "caipiaoid")
Add-Mapping -Map $aliasToCanonical -Canonical "flight_no" -Aliases @("flight_no", "fnum", "anum")
Add-Mapping -Map $aliasToCanonical -Canonical "page_num" -Aliases @("page_no", "page_num", "pageNum")
Add-Mapping -Map $aliasToCanonical -Canonical "page_size" -Aliases @("pageSize", "result_limit")
Add-Mapping -Map $aliasToCanonical -Canonical "line_thickness" -Aliases @("line_thickness", "thickness")
Add-Mapping -Map $aliasToCanonical -Canonical "bbox" -Aliases @("bbox", "crop_bbox", "crop_box", "box", "boxes")
Add-Mapping -Map $aliasToCanonical -Canonical "poi_id" -Aliases @("poiId")

$stableSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
$normalizationRules = [System.Collections.Generic.List[object]]::new()
$ruleCanonSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)

foreach ($slot in $rawWhitelist) {
    if ([string]::IsNullOrWhiteSpace($slot)) { continue }
    $name = $slot.Trim()
    if ($aliasToCanonical.ContainsKey($name)) {
        [void]$stableSet.Add($aliasToCanonical[$name])
    } else {
        [void]$stableSet.Add($name)
    }
}

$canonicalGroups = @{}
foreach ($kv in $aliasToCanonical.GetEnumerator()) {
    $alias = $kv.Key
    $canonical = $kv.Value
    if (-not $canonicalGroups.ContainsKey($canonical)) {
        $canonicalGroups[$canonical] = New-Object System.Collections.Generic.List[string]
    }
    $canonicalGroups[$canonical].Add($alias)
}

foreach ($c in ($canonicalGroups.Keys | Sort-Object)) {
    $aliases = @($canonicalGroups[$c] | Sort-Object)
    $normalizationRules.Add([ordered]@{
        canonical = $c
        aliases = $aliases
    }) | Out-Null
}

# 通用槽位（<=20），用于意图阶段实体抽取的“公共记忆键”
$commonSlots = @(
    "query",
    "name",
    "type",
    "id",
    "code",
    "number",
    "url",
    "image_url",
    "city",
    "province",
    "address",
    "location",
    "origin",
    "destination",
    "date",
    "time",
    "timezone",
    "amount",
    "currency",
    "mobile"
)

$stableSlots = @($stableSet | Sort-Object)

$result = [ordered]@{
    generatedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
    source = [ordered]@{
        whitelist = (Resolve-Path $inputWhitelist).Path
        toolsListMd = (Test-Path $inputToolsMd) ? (Resolve-Path $inputToolsMd).Path : $inputToolsMd
    }
    policy = [ordered]@{
        normalization = "partial-manual-rules"
        note = "仅合并高置信同义项，避免过度归并；其余槽位保持原名。"
    }
    normalizationRules = $normalizationRules
    commonSlots = $commonSlots
    stableSlots = $stableSlots
    stats = [ordered]@{
        rawWhitelistCount = $rawWhitelist.Count
        stableSlotCount = $stableSlots.Count
        commonSlotCount = $commonSlots.Count
    }
}

$outputDir = Split-Path -Parent $outputFile
if (-not (Test-Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
}

$jsonOut = $result | ConvertTo-Json -Depth 12
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($outputFile, $jsonOut, $utf8NoBom)

Write-Output "Generated: $outputFile"
Write-Output ("Stable slots: {0}, Common slots: {1}" -f $stableSlots.Count, $commonSlots.Count)
