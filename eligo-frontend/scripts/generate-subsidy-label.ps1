param(
  [string]$OutputPath = "src\static\eligo\subsidy-label.svg"
)

Add-Type -AssemblyName System.Drawing

$text = [string]::Concat(
  ([int[]]@(0x5E73, 0x53F0, 0x8865, 0x8D34, 0x38, 0x30, 0x25, 0x20, 0xB7, 0x20, 0x7CBE, 0x9009, 0x6D3B, 0x52A8) |
    ForEach-Object { [char]$_ })
)
$targetWidth = 161.0
$targetHeight = 15.497610092163086
$family = [System.Drawing.FontFamily]::new("Noto Sans SC")
$format = [System.Drawing.StringFormat]::GenericTypographic
$format.FormatFlags = $format.FormatFlags -bor [System.Drawing.StringFormatFlags]::MeasureTrailingSpaces
$path = [System.Drawing.Drawing2D.GraphicsPath]::new()
$path.AddString(
  $text,
  $family,
  [int][System.Drawing.FontStyle]::Bold,
  16.0,
  [System.Drawing.PointF]::new(0, 0),
  $format
)

$bounds = $path.GetBounds()
$scaleX = $targetWidth / $bounds.Width
$scaleY = $targetHeight / $bounds.Height
$points = $path.PathPoints
$types = $path.PathTypes
$culture = [System.Globalization.CultureInfo]::InvariantCulture

function Format-Number([double]$value) {
  return $value.ToString("0.###", $culture)
}

function Transform-X([double]$value) {
  return ($value - $bounds.X) * $scaleX
}

function Transform-Y([double]$value) {
  return ($value - $bounds.Y) * $scaleY
}

$commands = [System.Collections.Generic.List[string]]::new()
$index = 0
while ($index -lt $points.Length) {
  $kind = $types[$index] -band 0x07
  $closed = ($types[$index] -band 0x80) -ne 0

  if ($kind -eq 0) {
    $commands.Add(
      "M$(Format-Number (Transform-X $points[$index].X)) $(Format-Number (Transform-Y $points[$index].Y))"
    )
    if ($closed) { $commands.Add("Z") }
    $index += 1
    continue
  }

  if ($kind -eq 1) {
    $commands.Add(
      "L$(Format-Number (Transform-X $points[$index].X)) $(Format-Number (Transform-Y $points[$index].Y))"
    )
    if ($closed) { $commands.Add("Z") }
    $index += 1
    continue
  }

  if ($kind -eq 3 -and ($index + 2) -lt $points.Length) {
    $p1 = $points[$index]
    $p2 = $points[$index + 1]
    $p3 = $points[$index + 2]
    $commands.Add(
      "C$(Format-Number (Transform-X $p1.X)) $(Format-Number (Transform-Y $p1.Y)) " +
      "$(Format-Number (Transform-X $p2.X)) $(Format-Number (Transform-Y $p2.Y)) " +
      "$(Format-Number (Transform-X $p3.X)) $(Format-Number (Transform-Y $p3.Y))"
    )
    if (($types[$index + 2] -band 0x80) -ne 0) { $commands.Add("Z") }
    $index += 3
    continue
  }

  $index += 1
}

$viewHeight = Format-Number $targetHeight
$pathData = $commands -join " "
$svg = @"
<svg xmlns="http://www.w3.org/2000/svg" width="161" height="$viewHeight" viewBox="0 0 161 $viewHeight">
  <path d="$pathData" fill="#FFFFFF"/>
</svg>
"@

$absoluteOutput = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $OutputPath))
$outputDirectory = [System.IO.Path]::GetDirectoryName($absoluteOutput)
[System.IO.Directory]::CreateDirectory($outputDirectory) | Out-Null
[System.IO.File]::WriteAllText($absoluteOutput, $svg, [System.Text.UTF8Encoding]::new($false))

$path.Dispose()
$format.Dispose()
$family.Dispose()

Write-Output $absoluteOutput
