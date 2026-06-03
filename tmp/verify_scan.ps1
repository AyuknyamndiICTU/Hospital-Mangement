$files = Get-ChildItem -Recurse -Filter *.md -ErrorAction SilentlyContinue
$out = @()

foreach ($f in $files) {
  $m = Select-String -Path $f.FullName -Pattern 'verification sweep|Verification sweep|VERIFICATION SWEEP|PASS|FAIL' -SimpleMatch -ErrorAction SilentlyContinue
  foreach ($x in $m) {
    $out += ($f.FullName + ' :: ' + $x.LineNumber + ' :: ' + $x.Line.Trim())
  }
}

$out | Select-Object -First 200
