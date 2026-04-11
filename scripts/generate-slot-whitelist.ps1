param(
    [string]$ProjectRoot = ".",
    [string]$OutputPath = "dataset/slot_whitelist.json",
    [int]$CommonThreshold = 3
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-SummaryPath {
    param([string]$Root)

    $appYml = Join-Path $Root "agent-business/src/main/resources/application.yml"
    $configured = $null
    if (Test-Path $appYml) {
        $line = Select-String -Path $appYml -Pattern "^\s*path:\s*(.+)$" -SimpleMatch:$false | Select-Object -First 1
        if ($line) {
            $configured = $line.Matches[0].Groups[1].Value.Trim()
        }
    }

    $candidates = New-Object System.Collections.Generic.List[string]
    if ($configured) { [void]$candidates.Add($configured) }
    [void]$candidates.Add("file:./dataset/mcp_final_summary.json")
    [void]$candidates.Add("file:../dataset/mcp_final_summary.json")
    [void]$candidates.Add("dataset/mcp_final_summary.json")
    [void]$candidates.Add("../dataset/mcp_final_summary.json")

    foreach ($candidate in $candidates) {
        $resolved = $candidate
        if ($candidate.StartsWith("classpath:")) {
            $sub = $candidate.Substring("classpath:".Length).TrimStart("/")
            $resolved = Join-Path $Root ("agent-business/src/main/resources/" + $sub)
        } elseif ($candidate.StartsWith("file:")) {
            $sub = $candidate.Substring("file:".Length)
            $resolved = Join-Path $Root $sub
        } elseif (-not [System.IO.Path]::IsPathRooted($candidate)) {
            $resolved = Join-Path $Root $candidate
        }

        if (Test-Path $resolved) {
            return (Resolve-Path $resolved).Path
        }
    }

    throw "Cannot find mcp_final_summary.json from configured/fallback paths."
}

function Add-Name {
    param(
        [System.Collections.Generic.HashSet[string]]$Set,
        [string]$Name
    )
    if ([string]::IsNullOrWhiteSpace($Name)) { return }
    $trimmed = $Name.Trim()
    if ($trimmed.Length -eq 0) { return }
    [void]$Set.Add($trimmed)
}

function Collect-SchemaNames {
    param(
        [object]$Node,
        [System.Collections.Generic.HashSet[string]]$Sink
    )

    if ($null -eq $Node) { return }

    $propNames = @($Node.PSObject.Properties | ForEach-Object { $_.Name })
    if ($propNames.Count -eq 0) { return }

    # Object schema: properties
    if (($propNames -contains "properties") -and $null -ne $Node.properties) {
        foreach ($p in $Node.properties.PSObject.Properties) {
            Add-Name -Set $Sink -Name $p.Name
            Collect-SchemaNames -Node $p.Value -Sink $Sink
        }
    }

    # Array schema: items
    if (($propNames -contains "items") -and $null -ne $Node.items) {
        Collect-SchemaNames -Node $Node.items -Sink $Sink
    }

    # anyOf / oneOf / allOf
    foreach ($k in @("anyOf", "oneOf", "allOf")) {
        if (($propNames -contains $k) -and $null -ne $Node.$k) {
            foreach ($item in @($Node.$k)) {
                Collect-SchemaNames -Node $item -Sink $Sink
            }
        }
    }
}

$root = (Resolve-Path $ProjectRoot).Path
$summaryPath = Get-SummaryPath -Root $root

$json = Get-Content -Raw -Path $summaryPath | ConvertFrom-Json
$skills = @($json)

$toolSlotSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
$schemaFreq = @{}
$toolCount = 0

foreach ($skill in $skills) {
    $tools = @($skill.tools)
    foreach ($tool in $tools) {
        $toolCount++
        $perToolSchemaNames = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)

        foreach ($slot in @($tool.inputSlots)) {
            Add-Name -Set $toolSlotSet -Name $slot.slotKey
        }

        if ($null -ne $tool.inputSchema) {
            Collect-SchemaNames -Node $tool.inputSchema -Sink $perToolSchemaNames
        }

        foreach ($name in $perToolSchemaNames) {
            Add-Name -Set $toolSlotSet -Name $name
            if ($schemaFreq.ContainsKey($name)) {
                $schemaFreq[$name] = [int]$schemaFreq[$name] + 1
            } else {
                $schemaFreq[$name] = 1
            }
        }
    }
}

$commonSlots = @(
    $schemaFreq.GetEnumerator() |
        Where-Object { $_.Value -ge $CommonThreshold } |
        Sort-Object -Property Name |
        ForEach-Object { $_.Name }
)

$toolSlots = @($toolSlotSet | Sort-Object)
$allSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
foreach ($n in $toolSlots) { [void]$allSet.Add($n) }
foreach ($n in $commonSlots) { [void]$allSet.Add($n) }
$allWhitelist = @($allSet | Sort-Object)

$result = [ordered]@{
    generatedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
    sourceSummaryPath = $summaryPath
    stats = [ordered]@{
        skillCount = $skills.Count
        toolCount = $toolCount
        toolSlotCount = $toolSlots.Count
        commonSlotCount = $commonSlots.Count
        whitelistCount = $allWhitelist.Count
        commonThreshold = $CommonThreshold
    }
    commonSlots = $commonSlots
    toolSlots = $toolSlots
    whitelist = $allWhitelist
}

$outputFile = Join-Path $root $OutputPath
$outputDir = Split-Path -Parent $outputFile
if (-not (Test-Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
}

$jsonOut = $result | ConvertTo-Json -Depth 8
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($outputFile, $jsonOut, $utf8NoBom)

Write-Output "Generated: $outputFile"
Write-Output ("Summary: skills={0}, tools={1}, whitelist={2}" -f $skills.Count, $toolCount, $allWhitelist.Count)
