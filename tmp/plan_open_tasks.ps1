param(
  [string]$Path = "plan.md"
)

$lines = Get-Content -LiteralPath $Path
$phase = 0
$open = @()

foreach($l in $lines){
  if($l -match '^## Phase (\d+)\s*—'){
    $phase = [int]$matches[1]
    continue
  }
  if($l -match '^- \[ \] (.+)$'){
    $title = $matches[1].Trim()
    $open += [pscustomobject]@{ Phase = $phase; Title = $title }
  }
}

$changed = 1
$stillOpen = $open.Count

"changed=$changed"
"still_open=$stillOpen"
$open | ForEach-Object { "Phase $($_.Phase): $($_.Title)" }
